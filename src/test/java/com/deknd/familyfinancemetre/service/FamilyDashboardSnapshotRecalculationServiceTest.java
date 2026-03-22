package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.FamilyDashboardSnapshotEntity;
import com.deknd.familyfinancemetre.entity.FamilyEntity;
import com.deknd.familyfinancemetre.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.entity.MemberFinanceSnapshotEntity;
import com.deknd.familyfinancemetre.entity.enums.DashboardStatus;
import com.deknd.familyfinancemetre.repository.FamilyDashboardSnapshotRepository;
import com.deknd.familyfinancemetre.repository.MemberFinanceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FamilyDashboardSnapshotRecalculationServiceTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID FIRST_MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SECOND_MEMBER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID DASHBOARD_SNAPSHOT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final Clock FIXED_CLOCK = Clock.fixed(
		Instant.parse("2026-03-22T07:15:30Z"),
		ZoneId.of("Europe/Moscow")
	);

	@Mock
	private MemberFinanceSnapshotRepository memberFinanceSnapshotRepository;

	@Mock
	private FamilyDashboardSnapshotRepository familyDashboardSnapshotRepository;

	private FamilyDashboardSnapshotRecalculationService familyDashboardSnapshotRecalculationService;

	@BeforeEach
	void setUp() {
		familyDashboardSnapshotRecalculationService = new FamilyDashboardSnapshotRecalculationService(
			memberFinanceSnapshotRepository,
			familyDashboardSnapshotRepository,
			FIXED_CLOCK
		);
	}

	@Test
	@DisplayName("Создает семейный snapshot из одного персонального snapshot")
	void recalculateForFamilyPeriodCreatesDashboardSnapshotFromSingleMemberSnapshot() {
		MemberFinanceSnapshotEntity memberSnapshot = memberSnapshot(FIRST_MEMBER_ID, 120000, 50000, 18000, 150000);

		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of(memberSnapshot));
		given(familyDashboardSnapshotRepository.findByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		ArgumentCaptor<FamilyDashboardSnapshotEntity> snapshotCaptor =
			ArgumentCaptor.forClass(FamilyDashboardSnapshotEntity.class);
		verify(familyDashboardSnapshotRepository).save(snapshotCaptor.capture());

		FamilyDashboardSnapshotEntity savedSnapshot = snapshotCaptor.getValue();
		assertThat(savedSnapshot.getId()).isNull();
		assertThat(savedSnapshot.getFamily().getId()).isEqualTo(FAMILY_ID);
		assertThat(savedSnapshot.getPeriodYear()).isEqualTo(2026);
		assertThat(savedSnapshot.getPeriodMonth()).isEqualTo((short) 3);
		assertThat(savedSnapshot.getStatus()).isEqualTo(DashboardStatus.NORMAL);
		assertThat(savedSnapshot.getStatusText()).isEqualTo("Норма");
		assertThat(savedSnapshot.getStatusReason()).isEqualTo("Policy статуса будет определен отдельно");
		assertThat(savedSnapshot.getMonthlyIncome()).isEqualTo(120000);
		assertThat(savedSnapshot.getMonthlyExpenses()).isEqualTo(50000);
		assertThat(savedSnapshot.getCreditLoadPercent()).isEqualByComparingTo("15.00");
		assertThat(savedSnapshot.getEmergencyFundMonths()).isEqualByComparingTo("3.00");
		assertThat(savedSnapshot.getMemberCountUsed()).isEqualTo(1);
		assertThat(savedSnapshot.getCalculatedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
	}

	@Test
	@DisplayName("Суммирует значения всех членов семьи за период")
	void recalculateForFamilyPeriodSumsAllFamilyMemberSnapshots() {
		MemberFinanceSnapshotEntity firstSnapshot = memberSnapshot(FIRST_MEMBER_ID, 120000, 50000, 12000, 150000);
		MemberFinanceSnapshotEntity secondSnapshot = memberSnapshot(SECOND_MEMBER_ID, 80000, 40000, 8000, 50000);

		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of(firstSnapshot, secondSnapshot));
		given(familyDashboardSnapshotRepository.findByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		ArgumentCaptor<FamilyDashboardSnapshotEntity> snapshotCaptor =
			ArgumentCaptor.forClass(FamilyDashboardSnapshotEntity.class);
		verify(familyDashboardSnapshotRepository).save(snapshotCaptor.capture());

		FamilyDashboardSnapshotEntity savedSnapshot = snapshotCaptor.getValue();
		assertThat(savedSnapshot.getMonthlyIncome()).isEqualTo(200000);
		assertThat(savedSnapshot.getMonthlyExpenses()).isEqualTo(90000);
		assertThat(savedSnapshot.getCreditLoadPercent()).isEqualByComparingTo("10.00");
		assertThat(savedSnapshot.getEmergencyFundMonths()).isEqualByComparingTo("2.22");
		assertThat(savedSnapshot.getMemberCountUsed()).isEqualTo(2);
	}

	@Test
	@DisplayName("Округляет кредитную нагрузку по правилу HALF_UP")
	void recalculateForFamilyPeriodCalculatesCreditLoadPercentWithHalfUpRounding() {
		MemberFinanceSnapshotEntity memberSnapshot = memberSnapshot(FIRST_MEMBER_ID, 30000, 10000, 5000, 100000);

		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of(memberSnapshot));
		given(familyDashboardSnapshotRepository.findByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		ArgumentCaptor<FamilyDashboardSnapshotEntity> snapshotCaptor =
			ArgumentCaptor.forClass(FamilyDashboardSnapshotEntity.class);
		verify(familyDashboardSnapshotRepository).save(snapshotCaptor.capture());

		assertThat(snapshotCaptor.getValue().getCreditLoadPercent()).isEqualByComparingTo("16.67");
	}

	@Test
	@DisplayName("При нулевом доходе и наличии кредитов сохраняет кредитную нагрузку 100 процентов")
	void recalculateForFamilyPeriodReturnsHundredPercentCreditLoadWhenIncomeIsZeroAndCreditsExist() {
		MemberFinanceSnapshotEntity memberSnapshot = memberSnapshot(FIRST_MEMBER_ID, 0, 10000, 5000, 25000);

		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of(memberSnapshot));
		given(familyDashboardSnapshotRepository.findByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		ArgumentCaptor<FamilyDashboardSnapshotEntity> snapshotCaptor =
			ArgumentCaptor.forClass(FamilyDashboardSnapshotEntity.class);
		verify(familyDashboardSnapshotRepository).save(snapshotCaptor.capture());

		assertThat(snapshotCaptor.getValue().getCreditLoadPercent()).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("При нулевом доходе и отсутствии кредитов сохраняет нулевую кредитную нагрузку")
	void recalculateForFamilyPeriodReturnsZeroCreditLoadWhenIncomeAndCreditsAreZero() {
		MemberFinanceSnapshotEntity memberSnapshot = memberSnapshot(FIRST_MEMBER_ID, 0, 10000, 0, 25000);

		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of(memberSnapshot));
		given(familyDashboardSnapshotRepository.findByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		ArgumentCaptor<FamilyDashboardSnapshotEntity> snapshotCaptor =
			ArgumentCaptor.forClass(FamilyDashboardSnapshotEntity.class);
		verify(familyDashboardSnapshotRepository).save(snapshotCaptor.capture());

		assertThat(snapshotCaptor.getValue().getCreditLoadPercent()).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("При нулевых расходах сохраняет нулевое значение подушки")
	void recalculateForFamilyPeriodReturnsZeroEmergencyFundMonthsWhenExpensesAreZero() {
		MemberFinanceSnapshotEntity memberSnapshot = memberSnapshot(FIRST_MEMBER_ID, 70000, 0, 5000, 25000);

		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of(memberSnapshot));
		given(familyDashboardSnapshotRepository.findByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		ArgumentCaptor<FamilyDashboardSnapshotEntity> snapshotCaptor =
			ArgumentCaptor.forClass(FamilyDashboardSnapshotEntity.class);
		verify(familyDashboardSnapshotRepository).save(snapshotCaptor.capture());

		assertThat(snapshotCaptor.getValue().getEmergencyFundMonths()).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("Обновляет существующий семейный snapshot вместо создания нового")
	void recalculateForFamilyPeriodUpdatesExistingDashboardSnapshot() {
		MemberFinanceSnapshotEntity memberSnapshot = memberSnapshot(FIRST_MEMBER_ID, 90000, 40000, 10000, 80000);
		FamilyDashboardSnapshotEntity existingSnapshot = new FamilyDashboardSnapshotEntity();
		existingSnapshot.setId(DASHBOARD_SNAPSHOT_ID);
		existingSnapshot.setPeriodYear(2026);
		existingSnapshot.setPeriodMonth((short) 3);
		existingSnapshot.setMonthlyIncome(1);

		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of(memberSnapshot));
		given(familyDashboardSnapshotRepository.findByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(Optional.of(existingSnapshot));

		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		ArgumentCaptor<FamilyDashboardSnapshotEntity> snapshotCaptor =
			ArgumentCaptor.forClass(FamilyDashboardSnapshotEntity.class);
		verify(familyDashboardSnapshotRepository).save(snapshotCaptor.capture());

		FamilyDashboardSnapshotEntity savedSnapshot = snapshotCaptor.getValue();
		assertThat(savedSnapshot).isSameAs(existingSnapshot);
		assertThat(savedSnapshot.getId()).isEqualTo(DASHBOARD_SNAPSHOT_ID);
		assertThat(savedSnapshot.getMonthlyIncome()).isEqualTo(90000);
		assertThat(savedSnapshot.getMonthlyExpenses()).isEqualTo(40000);
		assertThat(savedSnapshot.getCreditLoadPercent()).isEqualByComparingTo("11.11");
		assertThat(savedSnapshot.getEmergencyFundMonths()).isEqualByComparingTo("2.00");
	}

	@Test
	@DisplayName("Бросает ошибку, если для семьи нет персональных snapshot-ов за период")
	void recalculateForFamilyPeriodThrowsWhenThereAreNoMemberSnapshotsForPeriod() {
		given(memberFinanceSnapshotRepository.findAllByFamilyIdAndPeriodYearAndPeriodMonth(FAMILY_ID, 2026, (short) 3))
			.willReturn(List.of());

		assertThatThrownBy(() -> familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No member finance snapshots found for family %s and period 2026-03".formatted(FAMILY_ID));
	}

	private MemberFinanceSnapshotEntity memberSnapshot(
		UUID memberId,
		int monthlyIncome,
		int monthlyExpenses,
		int monthlyCreditPayments,
		int liquidSavings
	) {
		FamilyEntity family = new FamilyEntity();
		family.setId(FAMILY_ID);

		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setId(memberId);
		member.setFamily(family);

		FinanceSubmissionEntity sourceSubmission = new FinanceSubmissionEntity();
		sourceSubmission.setId(UUID.randomUUID());

		MemberFinanceSnapshotEntity snapshot = new MemberFinanceSnapshotEntity();
		snapshot.setFamily(family);
		snapshot.setMember(member);
		snapshot.setSourceSubmission(sourceSubmission);
		snapshot.setPeriodYear(2026);
		snapshot.setPeriodMonth((short) 3);
		snapshot.setMonthlyIncome(monthlyIncome);
		snapshot.setMonthlyExpenses(monthlyExpenses);
		snapshot.setMonthlyCreditPayments(monthlyCreditPayments);
		snapshot.setLiquidSavings(liquidSavings);
		snapshot.setCollectedAt(OffsetDateTime.parse("2026-03-20T09:15:00+03:00"));
		return snapshot;
	}
}
