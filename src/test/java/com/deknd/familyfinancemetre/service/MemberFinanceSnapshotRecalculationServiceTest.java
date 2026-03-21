package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.FamilyEntity;
import com.deknd.familyfinancemetre.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.entity.MemberFinanceSnapshotEntity;
import com.deknd.familyfinancemetre.repository.FinanceSubmissionRepository;
import com.deknd.familyfinancemetre.repository.MemberFinanceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberFinanceSnapshotRecalculationServiceTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID FIRST_SUBMISSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID SECOND_SUBMISSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID SNAPSHOT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

	@Mock
	private FinanceSubmissionRepository financeSubmissionRepository;

	@Mock
	private MemberFinanceSnapshotRepository memberFinanceSnapshotRepository;

	@InjectMocks
	private MemberFinanceSnapshotRecalculationService memberFinanceSnapshotRecalculationService;

	@Test
	void recalculateForMemberPeriodCreatesSnapshotFromSingleSubmission() {
		FinanceSubmissionEntity submission = submission(
			FIRST_SUBMISSION_ID,
			2026,
			(short) 3,
			OffsetDateTime.parse("2026-03-15T08:40:00+03:00"),
			120000,
			50000,
			18000,
			150000,
			OffsetDateTime.parse("2026-03-15T08:45:00+03:00")
		);

		given(financeSubmissionRepository.findAllByMemberIdAndPeriodYearAndPeriodMonthOrderByCollectedAtDescCreatedAtDescIdDesc(
			MEMBER_ID,
			2026,
			(short) 3
		)).willReturn(List.of(submission));
		given(memberFinanceSnapshotRepository.findByMemberIdAndPeriodYearAndPeriodMonth(MEMBER_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		memberFinanceSnapshotRecalculationService.recalculateForMemberPeriod(MEMBER_ID, 2026, (short) 3);

		ArgumentCaptor<MemberFinanceSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(MemberFinanceSnapshotEntity.class);
		verify(memberFinanceSnapshotRepository).save(snapshotCaptor.capture());

		MemberFinanceSnapshotEntity savedSnapshot = snapshotCaptor.getValue();
		assertThat(savedSnapshot.getId()).isNull();
		assertThat(savedSnapshot.getFamily().getId()).isEqualTo(FAMILY_ID);
		assertThat(savedSnapshot.getMember().getId()).isEqualTo(MEMBER_ID);
		assertThat(savedSnapshot.getPeriodYear()).isEqualTo(2026);
		assertThat(savedSnapshot.getPeriodMonth()).isEqualTo((short) 3);
		assertThat(savedSnapshot.getSourceSubmission().getId()).isEqualTo(FIRST_SUBMISSION_ID);
		assertThat(savedSnapshot.getMonthlyIncome()).isEqualTo(120000);
		assertThat(savedSnapshot.getMonthlyExpenses()).isEqualTo(50000);
		assertThat(savedSnapshot.getMonthlyCreditPayments()).isEqualTo(18000);
		assertThat(savedSnapshot.getLiquidSavings()).isEqualTo(150000);
		assertThat(savedSnapshot.getCollectedAt()).isEqualTo(OffsetDateTime.parse("2026-03-15T08:40:00+03:00"));
	}

	@Test
	void recalculateForMemberPeriodSumsIncomeAndUsesFreshestSubmissionValues() {
		FinanceSubmissionEntity freshestSubmission = submission(
			FIRST_SUBMISSION_ID,
			2026,
			(short) 3,
			OffsetDateTime.parse("2026-03-20T09:15:00+03:00"),
			70000,
			62000,
			15000,
			200000,
			OffsetDateTime.parse("2026-03-20T09:20:00+03:00")
		);
		FinanceSubmissionEntity olderSubmission = submission(
			SECOND_SUBMISSION_ID,
			2026,
			(short) 3,
			OffsetDateTime.parse("2026-03-10T08:30:00+03:00"),
			50000,
			48000,
			18000,
			150000,
			OffsetDateTime.parse("2026-03-21T10:00:00+03:00")
		);

		given(financeSubmissionRepository.findAllByMemberIdAndPeriodYearAndPeriodMonthOrderByCollectedAtDescCreatedAtDescIdDesc(
			MEMBER_ID,
			2026,
			(short) 3
		)).willReturn(List.of(freshestSubmission, olderSubmission));
		given(memberFinanceSnapshotRepository.findByMemberIdAndPeriodYearAndPeriodMonth(MEMBER_ID, 2026, (short) 3))
			.willReturn(Optional.empty());

		memberFinanceSnapshotRecalculationService.recalculateForMemberPeriod(MEMBER_ID, 2026, (short) 3);

		ArgumentCaptor<MemberFinanceSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(MemberFinanceSnapshotEntity.class);
		verify(memberFinanceSnapshotRepository).save(snapshotCaptor.capture());

		MemberFinanceSnapshotEntity savedSnapshot = snapshotCaptor.getValue();
		assertThat(savedSnapshot.getMonthlyIncome()).isEqualTo(120000);
		assertThat(savedSnapshot.getMonthlyExpenses()).isEqualTo(62000);
		assertThat(savedSnapshot.getMonthlyCreditPayments()).isEqualTo(15000);
		assertThat(savedSnapshot.getLiquidSavings()).isEqualTo(200000);
		assertThat(savedSnapshot.getSourceSubmission().getId()).isEqualTo(FIRST_SUBMISSION_ID);
		assertThat(savedSnapshot.getCollectedAt()).isEqualTo(OffsetDateTime.parse("2026-03-20T09:15:00+03:00"));
	}

	@Test
	void recalculateForMemberPeriodUpdatesExistingSnapshotWithoutCreatingNewOne() {
		FinanceSubmissionEntity submission = submission(
			FIRST_SUBMISSION_ID,
			2026,
			(short) 3,
			OffsetDateTime.parse("2026-03-22T08:00:00+03:00"),
			130000,
			70000,
			21000,
			240000,
			OffsetDateTime.parse("2026-03-22T08:05:00+03:00")
		);
		MemberFinanceSnapshotEntity existingSnapshot = new MemberFinanceSnapshotEntity();
		existingSnapshot.setId(SNAPSHOT_ID);
		existingSnapshot.setPeriodYear(2026);
		existingSnapshot.setPeriodMonth((short) 3);
		existingSnapshot.setMonthlyIncome(1);

		given(financeSubmissionRepository.findAllByMemberIdAndPeriodYearAndPeriodMonthOrderByCollectedAtDescCreatedAtDescIdDesc(
			MEMBER_ID,
			2026,
			(short) 3
		)).willReturn(List.of(submission));
		given(memberFinanceSnapshotRepository.findByMemberIdAndPeriodYearAndPeriodMonth(MEMBER_ID, 2026, (short) 3))
			.willReturn(Optional.of(existingSnapshot));

		memberFinanceSnapshotRecalculationService.recalculateForMemberPeriod(MEMBER_ID, 2026, (short) 3);

		ArgumentCaptor<MemberFinanceSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(MemberFinanceSnapshotEntity.class);
		verify(memberFinanceSnapshotRepository).save(snapshotCaptor.capture());

		MemberFinanceSnapshotEntity savedSnapshot = snapshotCaptor.getValue();
		assertThat(savedSnapshot).isSameAs(existingSnapshot);
		assertThat(savedSnapshot.getId()).isEqualTo(SNAPSHOT_ID);
		assertThat(savedSnapshot.getMonthlyIncome()).isEqualTo(130000);
		assertThat(savedSnapshot.getMonthlyExpenses()).isEqualTo(70000);
		assertThat(savedSnapshot.getMonthlyCreditPayments()).isEqualTo(21000);
		assertThat(savedSnapshot.getLiquidSavings()).isEqualTo(240000);
		assertThat(savedSnapshot.getSourceSubmission().getId()).isEqualTo(FIRST_SUBMISSION_ID);
	}

	@Test
	void recalculateForMemberPeriodThrowsWhenThereAreNoSubmissionsForPeriod() {
		given(financeSubmissionRepository.findAllByMemberIdAndPeriodYearAndPeriodMonthOrderByCollectedAtDescCreatedAtDescIdDesc(
			MEMBER_ID,
			2026,
			(short) 3
		)).willReturn(List.of());

		assertThatThrownBy(() -> memberFinanceSnapshotRecalculationService.recalculateForMemberPeriod(MEMBER_ID, 2026, (short) 3))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No finance submissions found for member %s and period 2026-03".formatted(MEMBER_ID));
	}

	private FinanceSubmissionEntity submission(
		UUID submissionId,
		int periodYear,
		short periodMonth,
		OffsetDateTime collectedAt,
		int monthlyIncome,
		int monthlyExpenses,
		int monthlyCreditPayments,
		int liquidSavings,
		OffsetDateTime createdAt
	) {
		FamilyEntity family = new FamilyEntity();
		family.setId(FAMILY_ID);

		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setId(MEMBER_ID);
		member.setFamily(family);

		FinanceSubmissionEntity submission = new FinanceSubmissionEntity();
		submission.setId(submissionId);
		submission.setCreatedAt(createdAt);
		submission.setFamily(family);
		submission.setMember(member);
		submission.setPeriodYear(periodYear);
		submission.setPeriodMonth(periodMonth);
		submission.setCollectedAt(collectedAt);
		submission.setMonthlyIncome(monthlyIncome);
		submission.setMonthlyExpenses(monthlyExpenses);
		submission.setMonthlyCreditPayments(monthlyCreditPayments);
		submission.setLiquidSavings(liquidSavings);
		return submission;
	}
}
