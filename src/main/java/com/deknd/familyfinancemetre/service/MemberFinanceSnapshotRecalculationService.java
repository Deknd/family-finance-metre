package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.entity.MemberFinanceSnapshotEntity;
import com.deknd.familyfinancemetre.repository.FinanceSubmissionRepository;
import com.deknd.familyfinancemetre.repository.MemberFinanceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberFinanceSnapshotRecalculationService {

	private final FinanceSubmissionRepository financeSubmissionRepository;
	private final MemberFinanceSnapshotRepository memberFinanceSnapshotRepository;

	/**
	 * Пересчитывает месячный snapshot участника по всем сохраненным submission за указанный период.
	 *
	 * @param memberId идентификатор участника семьи
	 * @param periodYear год расчетного периода
	 * @param periodMonth месяц расчетного периода
	 */
	@Transactional
	public void recalculateForMemberPeriod(UUID memberId, int periodYear, short periodMonth) {
		List<FinanceSubmissionEntity> submissions = financeSubmissionRepository
			.findAllByMemberIdAndPeriodYearAndPeriodMonthOrderByCollectedAtDescCreatedAtDescIdDesc(
				memberId,
				periodYear,
				periodMonth
			);

		if (submissions.isEmpty()) {
			throw new IllegalStateException(
				"No finance submissions found for member %s and period %d-%02d"
					.formatted(memberId, periodYear, periodMonth)
			);
		}

		FinanceSubmissionEntity freshestSubmission = submissions.get(0);
		int totalMonthlyIncome = submissions.stream()
			.mapToInt(FinanceSubmissionEntity::getMonthlyIncome)
			.sum();

		MemberFinanceSnapshotEntity snapshot = memberFinanceSnapshotRepository
			.findByMemberIdAndPeriodYearAndPeriodMonth(memberId, periodYear, periodMonth)
			.orElseGet(MemberFinanceSnapshotEntity::new);

		snapshot.setFamily(freshestSubmission.getFamily());
		snapshot.setMember(freshestSubmission.getMember());
		snapshot.setPeriodYear(periodYear);
		snapshot.setPeriodMonth(periodMonth);
		snapshot.setSourceSubmission(freshestSubmission);
		snapshot.setMonthlyIncome(totalMonthlyIncome);
		snapshot.setMonthlyExpenses(freshestSubmission.getMonthlyExpenses());
		snapshot.setMonthlyCreditPayments(freshestSubmission.getMonthlyCreditPayments());
		snapshot.setLiquidSavings(freshestSubmission.getLiquidSavings());
		snapshot.setCollectedAt(freshestSubmission.getCollectedAt());

		memberFinanceSnapshotRepository.save(snapshot);
	}
}
