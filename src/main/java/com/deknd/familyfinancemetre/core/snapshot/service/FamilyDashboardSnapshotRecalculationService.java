package com.deknd.familyfinancemetre.core.snapshot.service;

import com.deknd.familyfinancemetre.core.snapshot.entity.FamilyDashboardSnapshotEntity;
import com.deknd.familyfinancemetre.core.snapshot.entity.MemberFinanceSnapshotEntity;
import com.deknd.familyfinancemetre.core.snapshot.repository.FamilyDashboardSnapshotRepository;
import com.deknd.familyfinancemetre.core.snapshot.repository.MemberFinanceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyDashboardSnapshotRecalculationService {

	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
	private static final BigDecimal ZERO = new BigDecimal("0.00");

	private final MemberFinanceSnapshotRepository memberFinanceSnapshotRepository;
	private final FamilyDashboardSnapshotRepository familyDashboardSnapshotRepository;
	private final FamilyDashboardStatusPolicy familyDashboardStatusPolicy;
	private final Clock clock;

	/**
	 * Пересчитывает семейный dashboard snapshot по всем персональным snapshot-ам за указанный период.
	 *
	 * @param familyId идентификатор семьи
	 * @param periodYear год расчетного периода
	 * @param periodMonth месяц расчетного периода
	 */
	@Transactional
	public void recalculateForFamilyPeriod(UUID familyId, int periodYear, short periodMonth) {
		List<MemberFinanceSnapshotEntity> memberSnapshots = memberFinanceSnapshotRepository
			.findAllByFamilyIdAndPeriodYearAndPeriodMonth(familyId, periodYear, periodMonth);

		if (memberSnapshots.isEmpty()) {
			throw new IllegalStateException(
				"No member finance snapshots found for family %s and period %d-%02d"
					.formatted(familyId, periodYear, periodMonth)
			);
		}

		MemberFinanceSnapshotEntity firstSnapshot = memberSnapshots.getFirst();
		int totalMonthlyIncome = sumBy(memberSnapshots, MemberFinanceSnapshotEntity::getMonthlyIncome);
		int totalMonthlyExpenses = sumBy(memberSnapshots, MemberFinanceSnapshotEntity::getMonthlyExpenses);
		int totalMonthlyCreditPayments = sumBy(memberSnapshots, MemberFinanceSnapshotEntity::getMonthlyCreditPayments);
		int totalLiquidSavings = sumBy(memberSnapshots, MemberFinanceSnapshotEntity::getLiquidSavings);
		BigDecimal creditLoadPercent = calculateCreditLoadPercent(totalMonthlyIncome, totalMonthlyCreditPayments);
		BigDecimal emergencyFundMonths = calculateEmergencyFundMonths(totalMonthlyExpenses, totalLiquidSavings);
		FamilyDashboardStatusDecision statusDecision = familyDashboardStatusPolicy.evaluate(
			new FamilyDashboardStatusPolicyContext(
				totalMonthlyIncome,
				totalMonthlyExpenses,
				creditLoadPercent,
				emergencyFundMonths,
				memberSnapshots.size()
			)
		);

		FamilyDashboardSnapshotEntity snapshot = familyDashboardSnapshotRepository
			.findByFamilyIdAndPeriodYearAndPeriodMonth(familyId, periodYear, periodMonth)
			.orElseGet(FamilyDashboardSnapshotEntity::new);

		snapshot.setFamily(firstSnapshot.getFamily());
		snapshot.setPeriodYear(periodYear);
		snapshot.setPeriodMonth(periodMonth);
		snapshot.setStatus(statusDecision.getStatus());
		snapshot.setStatusText(statusDecision.getStatusText());
		snapshot.setStatusReason(statusDecision.getStatusReason());
		snapshot.setMonthlyIncome(totalMonthlyIncome);
		snapshot.setMonthlyExpenses(totalMonthlyExpenses);
		snapshot.setCreditLoadPercent(creditLoadPercent);
		snapshot.setEmergencyFundMonths(emergencyFundMonths);
		snapshot.setMemberCountUsed(memberSnapshots.size());
		snapshot.setCalculatedAt(OffsetDateTime.now(clock));

		familyDashboardSnapshotRepository.save(snapshot);
	}

	private int sumBy(List<MemberFinanceSnapshotEntity> memberSnapshots, SnapshotFieldExtractor fieldExtractor) {
		return memberSnapshots.stream()
			.mapToInt(fieldExtractor::extract)
			.sum();
	}

	private BigDecimal calculateCreditLoadPercent(int totalMonthlyIncome, int totalMonthlyCreditPayments) {
		if (totalMonthlyIncome == 0) {
			return totalMonthlyCreditPayments > 0 ? ONE_HUNDRED.setScale(2, RoundingMode.HALF_UP) : ZERO;
		}

		return BigDecimal.valueOf(totalMonthlyCreditPayments)
			.multiply(ONE_HUNDRED)
			.divide(BigDecimal.valueOf(totalMonthlyIncome), 2, RoundingMode.HALF_UP);
	}

	private BigDecimal calculateEmergencyFundMonths(int totalMonthlyExpenses, int totalLiquidSavings) {
		if (totalMonthlyExpenses == 0) {
			return ZERO;
		}

		return BigDecimal.valueOf(totalLiquidSavings)
			.divide(BigDecimal.valueOf(totalMonthlyExpenses), 2, RoundingMode.HALF_UP);
	}

	@FunctionalInterface
	private interface SnapshotFieldExtractor {

		int extract(MemberFinanceSnapshotEntity memberSnapshot);
	}
}


