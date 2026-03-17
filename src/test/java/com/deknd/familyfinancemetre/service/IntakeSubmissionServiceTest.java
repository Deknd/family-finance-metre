package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeAcceptedResponse;
import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeRequest;
import com.deknd.familyfinancemetre.dto.validation.ValidationErrorResponse.ValidationErrorDetail;
import com.deknd.familyfinancemetre.entity.FamilyEntity;
import com.deknd.familyfinancemetre.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.entity.enums.SubmissionConfidence;
import com.deknd.familyfinancemetre.entity.enums.SubmissionSource;
import com.deknd.familyfinancemetre.exception.DuplicateSubmissionException;
import com.deknd.familyfinancemetre.exception.InvalidIntakePayloadReferenceException;
import com.deknd.familyfinancemetre.repository.FamilyMemberRepository;
import com.deknd.familyfinancemetre.repository.FamilyRepository;
import com.deknd.familyfinancemetre.repository.FinanceSubmissionRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IntakeSubmissionServiceTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SUBMISSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Mock
	private FinanceSubmissionRepository financeSubmissionRepository;

	@Mock
	private FamilyRepository familyRepository;

	@Mock
	private FamilyMemberRepository familyMemberRepository;

	@InjectMocks
	private IntakeSubmissionService intakeSubmissionService;

	@Test
	void acceptSavesSubmissionAndReturnsStoredId() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);
		FamilyMemberEntity member = member(MEMBER_ID, family);

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		doAnswer(invocation -> {
			FinanceSubmissionEntity submission = invocation.getArgument(0);
			submission.setId(SUBMISSION_ID);
			return submission;
		}).when(financeSubmissionRepository).saveAndFlush(any(FinanceSubmissionEntity.class));

		UserFinanceIntakeAcceptedResponse response = intakeSubmissionService.accept(request);

		ArgumentCaptor<FinanceSubmissionEntity> submissionCaptor = ArgumentCaptor.forClass(FinanceSubmissionEntity.class);
		verify(financeSubmissionRepository).saveAndFlush(submissionCaptor.capture());

		FinanceSubmissionEntity savedSubmission = submissionCaptor.getValue();
		assertThat(savedSubmission.getExternalSubmissionId()).isEqualTo("n8n-run-2026-03-15-001");
		assertThat(savedSubmission.getFamily()).isSameAs(family);
		assertThat(savedSubmission.getMember()).isSameAs(member);
		assertThat(savedSubmission.getSource()).isEqualTo(SubmissionSource.TELEGRAM);
		assertThat(savedSubmission.getPeriodYear()).isEqualTo(2026);
		assertThat(savedSubmission.getPeriodMonth()).isEqualTo((short) 3);
		assertThat(savedSubmission.getCollectedAt()).hasToString("2026-03-15T08:40+03:00");
		assertThat(savedSubmission.getMonthlyIncome()).isEqualTo(120000);
		assertThat(savedSubmission.getMonthlyExpenses()).isEqualTo(50000);
		assertThat(savedSubmission.getMonthlyCreditPayments()).isEqualTo(18000);
		assertThat(savedSubmission.getLiquidSavings()).isEqualTo(150000);
		assertThat(savedSubmission.getConfidence()).isEqualTo(SubmissionConfidence.MEDIUM);
		assertThat(savedSubmission.getNotes()).isEqualTo("User provided approximate values");
		assertThat(savedSubmission.getRawPayload().get("external_submission_id").asText()).isEqualTo("n8n-run-2026-03-15-001");
		assertThat(savedSubmission.getRawPayload().has("request_id")).isFalse();
		assertThat(savedSubmission.getRequestId()).isNull();
		assertThat(savedSubmission.getLlmCollectionRequest()).isNull();

		assertThat(response.status()).isEqualTo("accepted");
		assertThat(response.submissionId()).isEqualTo(SUBMISSION_ID.toString());
		assertThat(response.familyId()).isEqualTo(FAMILY_ID.toString());
		assertThat(response.memberId()).isEqualTo(MEMBER_ID.toString());
		assertThat(response.recalculationScheduled()).isTrue();
	}

	@Test
	void acceptThrowsDuplicateSubmissionWhenExternalSubmissionIdAlreadyExists() {
		UserFinanceIntakeRequest request = validRequest();
		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(true);

		assertThatThrownBy(() -> intakeSubmissionService.accept(request))
			.isInstanceOf(DuplicateSubmissionException.class)
			.hasMessage(DuplicateSubmissionException.ERROR_MESSAGE);

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
		verify(familyRepository, never()).findById(any(UUID.class));
		verify(familyMemberRepository, never()).findById(any(UUID.class));
	}

	@Test
	void acceptThrowsDuplicateSubmissionWhenUniqueConstraintIsViolatedDuringSave() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);
		FamilyMemberEntity member = member(MEMBER_ID, family);

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		given(financeSubmissionRepository.saveAndFlush(any(FinanceSubmissionEntity.class)))
			.willThrow(new DataIntegrityViolationException(
				"duplicate external_submission_id",
				new ConstraintViolationException(
					"duplicate key value violates unique constraint",
					new SQLException("duplicate"),
					"insert into finance_submissions ...",
					"uq_finance_submissions_external_submission_id"
				)
			));

		assertThatThrownBy(() -> intakeSubmissionService.accept(request))
			.isInstanceOf(DuplicateSubmissionException.class)
			.hasMessage(DuplicateSubmissionException.ERROR_MESSAGE);
	}

	@Test
	void acceptPropagatesNonDuplicateDataIntegrityViolation() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);
		FamilyMemberEntity member = member(MEMBER_ID, family);
		DataIntegrityViolationException exception = new DataIntegrityViolationException("foreign key violation");

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		given(financeSubmissionRepository.saveAndFlush(any(FinanceSubmissionEntity.class))).willThrow(exception);

		assertThatThrownBy(() -> intakeSubmissionService.accept(request))
			.isSameAs(exception);

		verify(familyRepository).findById(eq(FAMILY_ID));
		verify(familyMemberRepository).findById(eq(MEMBER_ID));
	}

	@Test
	void acceptThrowsValidationErrorWhenFamilyDoesNotExist() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyMemberEntity member = member(MEMBER_ID, family(FAMILY_ID));

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.empty());
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.accept(request),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("family_id", "family does not exist"));

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
	}

	@Test
	void acceptSavesRequestIdWhenItIsPresentInPayload() {
		UserFinanceIntakeRequest request = validRequest("req-2026-03-15-member-anna");
		FamilyEntity family = family(FAMILY_ID);
		FamilyMemberEntity member = member(MEMBER_ID, family);

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		doAnswer(invocation -> {
			FinanceSubmissionEntity submission = invocation.getArgument(0);
			submission.setId(SUBMISSION_ID);
			return submission;
		}).when(financeSubmissionRepository).saveAndFlush(any(FinanceSubmissionEntity.class));

		intakeSubmissionService.accept(request);

		ArgumentCaptor<FinanceSubmissionEntity> submissionCaptor = ArgumentCaptor.forClass(FinanceSubmissionEntity.class);
		verify(financeSubmissionRepository).saveAndFlush(submissionCaptor.capture());

		FinanceSubmissionEntity savedSubmission = submissionCaptor.getValue();
		assertThat(savedSubmission.getRequestId()).isEqualTo("req-2026-03-15-member-anna");
		assertThat(savedSubmission.getRawPayload().get("request_id").asText()).isEqualTo("req-2026-03-15-member-anna");
	}

	@Test
	void acceptThrowsValidationErrorWhenMemberDoesNotExist() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.accept(request),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("member_id", "member does not exist"));

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
	}

	@Test
	void acceptThrowsValidationErrorWhenMemberBelongsToAnotherFamily() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);
		FamilyEntity anotherFamily = family(UUID.fromString("44444444-4444-4444-4444-444444444444"));
		FamilyMemberEntity member = member(MEMBER_ID, anotherFamily);

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.accept(request),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("member_id", "member does not belong to the specified family"));

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
	}

	@Test
	void acceptReturnsValidationErrorsInStableOrderWhenFamilyAndMemberDoNotExist() {
		UserFinanceIntakeRequest request = validRequest();

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.empty());
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.accept(request),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(
				tuple("family_id", "family does not exist"),
				tuple("member_id", "member does not exist")
			);

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
	}

	private UserFinanceIntakeRequest validRequest() {
		return validRequest(null);
	}

	private UserFinanceIntakeRequest validRequest(String requestId) {
		return new UserFinanceIntakeRequest(
			"n8n-run-2026-03-15-001",
			requestId,
			FAMILY_ID.toString(),
			MEMBER_ID.toString(),
			"telegram",
			"2026-03-15T08:40:00+03:00",
			new UserFinanceIntakeRequest.Period(2026, 3),
			new UserFinanceIntakeRequest.FinanceInput(120000, 50000, 18000, 150000),
			new UserFinanceIntakeRequest.Meta("123456789", "medium", "User provided approximate values")
		);
	}

	private FamilyEntity family(UUID familyId) {
		FamilyEntity family = new FamilyEntity();
		family.setId(familyId);
		return family;
	}

	private FamilyMemberEntity member(UUID memberId, FamilyEntity family) {
		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setId(memberId);
		member.setFamily(family);
		return member;
	}
}
