package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeRequest;
import com.deknd.familyfinancemetre.dto.validation.ValidationErrorResponse.ValidationErrorDetail;
import com.deknd.familyfinancemetre.entity.FamilyEntity;
import com.deknd.familyfinancemetre.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.entity.enums.SubmissionConfidence;
import com.deknd.familyfinancemetre.entity.enums.SubmissionSource;
import com.deknd.familyfinancemetre.exception.DuplicateSubmissionException;
import com.deknd.familyfinancemetre.exception.InvalidIntakePayloadReferenceException;
import com.deknd.familyfinancemetre.repository.FamilyMemberRepository;
import com.deknd.familyfinancemetre.repository.FamilyRepository;
import com.deknd.familyfinancemetre.repository.FinanceSubmissionRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
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
	private static final UUID LLM_REQUEST_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

	@Mock
	private FinanceSubmissionRepository financeSubmissionRepository;

	@Mock
	private FamilyRepository familyRepository;

	@Mock
	private FamilyMemberRepository familyMemberRepository;

	@InjectMocks
	private IntakeSubmissionService intakeSubmissionService;

	@Test
	@DisplayName("Сохраняет submission без request_id и без связи с llm request")
	void saveSubmissionPersistsPayloadWithoutCorrelationRequest() {
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

		FinanceSubmissionEntity savedSubmission = intakeSubmissionService.saveSubmission(request, null);

		ArgumentCaptor<FinanceSubmissionEntity> submissionCaptor = ArgumentCaptor.forClass(FinanceSubmissionEntity.class);
		verify(financeSubmissionRepository).saveAndFlush(submissionCaptor.capture());

		FinanceSubmissionEntity capturedSubmission = submissionCaptor.getValue();
		assertThat(capturedSubmission.getExternalSubmissionId()).isEqualTo("n8n-run-2026-03-15-001");
		assertThat(capturedSubmission.getFamily()).isSameAs(family);
		assertThat(capturedSubmission.getMember()).isSameAs(member);
		assertThat(capturedSubmission.getSource()).isEqualTo(SubmissionSource.TELEGRAM);
		assertThat(capturedSubmission.getPeriodYear()).isEqualTo(2026);
		assertThat(capturedSubmission.getPeriodMonth()).isEqualTo((short) 3);
		assertThat(capturedSubmission.getCollectedAt()).hasToString("2026-03-15T08:40+03:00");
		assertThat(capturedSubmission.getMonthlyIncome()).isEqualTo(120000);
		assertThat(capturedSubmission.getMonthlyExpenses()).isEqualTo(50000);
		assertThat(capturedSubmission.getMonthlyCreditPayments()).isEqualTo(18000);
		assertThat(capturedSubmission.getLiquidSavings()).isEqualTo(150000);
		assertThat(capturedSubmission.getConfidence()).isEqualTo(SubmissionConfidence.MEDIUM);
		assertThat(capturedSubmission.getNotes()).isEqualTo("User provided approximate values");
		assertThat(capturedSubmission.getRawPayload().get("external_submission_id").asText())
			.isEqualTo("n8n-run-2026-03-15-001");
		assertThat(capturedSubmission.getRawPayload().has("request_id")).isFalse();
		assertThat(capturedSubmission.getRequestId()).isNull();
		assertThat(capturedSubmission.getLlmCollectionRequest()).isNull();
		assertThat(savedSubmission).isSameAs(capturedSubmission);
		assertThat(savedSubmission.getId()).isEqualTo(SUBMISSION_ID);
	}

	@Test
	@DisplayName("Сохраняет request_id и связь с llm request, если orchestration уже нашел запрос")
	void saveSubmissionPersistsLinkedLlmCollectionRequest() {
		UserFinanceIntakeRequest request = validRequest("req-2026-03-15-member-anna");
		FamilyEntity family = family(FAMILY_ID);
		FamilyMemberEntity member = member(MEMBER_ID, family);
		LlmCollectionRequestEntity llmCollectionRequest = llmCollectionRequest();

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		doAnswer(invocation -> {
			FinanceSubmissionEntity submission = invocation.getArgument(0);
			submission.setId(SUBMISSION_ID);
			return submission;
		}).when(financeSubmissionRepository).saveAndFlush(any(FinanceSubmissionEntity.class));

		intakeSubmissionService.saveSubmission(request, llmCollectionRequest);

		ArgumentCaptor<FinanceSubmissionEntity> submissionCaptor = ArgumentCaptor.forClass(FinanceSubmissionEntity.class);
		verify(financeSubmissionRepository).saveAndFlush(submissionCaptor.capture());

		FinanceSubmissionEntity savedSubmission = submissionCaptor.getValue();
		assertThat(savedSubmission.getRequestId()).isEqualTo("req-2026-03-15-member-anna");
		assertThat(savedSubmission.getLlmCollectionRequest()).isSameAs(llmCollectionRequest);
		assertThat(savedSubmission.getRawPayload().get("request_id").asText()).isEqualTo("req-2026-03-15-member-anna");
	}

	@Test
	@DisplayName("Бросает conflict, если external_submission_id уже был обработан")
	void saveSubmissionThrowsDuplicateSubmissionWhenExternalSubmissionIdAlreadyExists() {
		UserFinanceIntakeRequest request = validRequest();
		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(true);

		assertThatThrownBy(() -> intakeSubmissionService.saveSubmission(request, null))
			.isInstanceOf(DuplicateSubmissionException.class)
			.hasMessage(DuplicateSubmissionException.ERROR_MESSAGE);

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
		verify(familyRepository, never()).findById(any(UUID.class));
		verify(familyMemberRepository, never()).findById(any(UUID.class));
	}

	@Test
	@DisplayName("Преобразует нарушение unique constraint при сохранении в DuplicateSubmissionException")
	void saveSubmissionThrowsDuplicateSubmissionWhenUniqueConstraintIsViolatedDuringSave() {
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

		assertThatThrownBy(() -> intakeSubmissionService.saveSubmission(request, null))
			.isInstanceOf(DuplicateSubmissionException.class)
			.hasMessage(DuplicateSubmissionException.ERROR_MESSAGE);
	}

	@Test
	@DisplayName("Пробрасывает другие DataIntegrityViolationException без маскировки")
	void saveSubmissionPropagatesNonDuplicateDataIntegrityViolation() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);
		FamilyMemberEntity member = member(MEMBER_ID, family);
		DataIntegrityViolationException exception = new DataIntegrityViolationException("foreign key violation");

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		given(financeSubmissionRepository.saveAndFlush(any(FinanceSubmissionEntity.class))).willThrow(exception);

		assertThatThrownBy(() -> intakeSubmissionService.saveSubmission(request, null))
			.isSameAs(exception);

		verify(familyRepository).findById(eq(FAMILY_ID));
		verify(familyMemberRepository).findById(eq(MEMBER_ID));
	}

	@Test
	@DisplayName("Возвращает validation error, если семья не найдена")
	void saveSubmissionThrowsValidationErrorWhenFamilyDoesNotExist() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyMemberEntity member = member(MEMBER_ID, family(FAMILY_ID));

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.empty());
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.saveSubmission(request, null),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("family_id", "family does not exist"));

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
	}

	@Test
	@DisplayName("Возвращает validation error, если участник не найден")
	void saveSubmissionThrowsValidationErrorWhenMemberDoesNotExist() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.saveSubmission(request, null),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("member_id", "member does not exist"));

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
	}

	@Test
	@DisplayName("Возвращает validation error, если участник принадлежит другой семье")
	void saveSubmissionThrowsValidationErrorWhenMemberBelongsToAnotherFamily() {
		UserFinanceIntakeRequest request = validRequest();
		FamilyEntity family = family(FAMILY_ID);
		FamilyEntity anotherFamily = family(UUID.fromString("55555555-5555-5555-5555-555555555555"));
		FamilyMemberEntity member = member(MEMBER_ID, anotherFamily);

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.of(family));
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.saveSubmission(request, null),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("member_id", "member does not belong to the specified family"));

		verify(financeSubmissionRepository, never()).saveAndFlush(any(FinanceSubmissionEntity.class));
	}

	@Test
	@DisplayName("Возвращает ошибки ссылок в стабильном порядке, если одновременно не найдены семья и участник")
	void saveSubmissionReturnsValidationErrorsInStableOrderWhenFamilyAndMemberDoNotExist() {
		UserFinanceIntakeRequest request = validRequest();

		given(financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())).willReturn(false);
		given(familyRepository.findById(FAMILY_ID)).willReturn(Optional.empty());
		given(familyMemberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> intakeSubmissionService.saveSubmission(request, null),
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

	private LlmCollectionRequestEntity llmCollectionRequest() {
		LlmCollectionRequestEntity llmCollectionRequest = new LlmCollectionRequestEntity();
		llmCollectionRequest.setId(LLM_REQUEST_ID);
		llmCollectionRequest.setRequestId("req-2026-03-15-member-anna");
		return llmCollectionRequest;
	}
}
