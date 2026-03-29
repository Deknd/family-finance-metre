package com.deknd.familyfinancemetre.flow.intake.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.collection.exception.LlmCollectionRequestInvalidStateException;
import com.deknd.familyfinancemetre.core.collection.exception.LlmCollectionRequestNotFoundException;
import com.deknd.familyfinancemetre.core.collection.service.LlmCollectionRequestLifecycleService;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.snapshot.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.core.snapshot.service.FamilyDashboardSnapshotRecalculationService;
import com.deknd.familyfinancemetre.core.snapshot.service.MemberFinanceSnapshotRecalculationService;
import com.deknd.familyfinancemetre.flow.intake.dto.UserFinanceIntakeAcceptedResponse;
import com.deknd.familyfinancemetre.flow.intake.dto.UserFinanceIntakeRequest;
import com.deknd.familyfinancemetre.flow.intake.exception.InvalidIntakePayloadReferenceException;
import com.deknd.familyfinancemetre.shared.web.error.ValidationErrorResponse.ValidationErrorDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserFinanceIntakeOrchestrationServiceTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SUBMISSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Mock
	private IntakeSubmissionService intakeSubmissionService;

	@Mock
	private MemberFinanceSnapshotRecalculationService memberFinanceSnapshotRecalculationService;

	@Mock
	private FamilyDashboardSnapshotRecalculationService familyDashboardSnapshotRecalculationService;

	@Mock
	private LlmCollectionRequestLifecycleService llmCollectionRequestLifecycleService;

	@Test
	@DisplayName("При intake без request_id сохраняет submission и пересчитывает оба snapshot")
	void acceptWithoutRequestIdPersistsSubmissionAndRecalculatesSnapshots() {
		UserFinanceIntakeOrchestrationService orchestrationService = orchestrationService();
		UserFinanceIntakeRequest request = validRequest();
		FinanceSubmissionEntity submission = submission();

		given(intakeSubmissionService.saveSubmission(request, null)).willReturn(submission);

		UserFinanceIntakeAcceptedResponse response = orchestrationService.accept(request);

		verify(intakeSubmissionService).ensureExternalSubmissionIdIsNew("n8n-run-2026-03-15-001");
		verify(intakeSubmissionService).saveSubmission(request, null);
		verify(llmCollectionRequestLifecycleService, never()).resolveAcceptedForIntake(any());
		verify(llmCollectionRequestLifecycleService, never()).markCompleted(any());
		verify(memberFinanceSnapshotRecalculationService).recalculateForMemberPeriod(MEMBER_ID, 2026, (short) 3);
		verify(familyDashboardSnapshotRecalculationService).recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);

		assertThat(response).isEqualTo(new UserFinanceIntakeAcceptedResponse(
			"accepted",
			SUBMISSION_ID.toString(),
			FAMILY_ID.toString(),
			MEMBER_ID.toString(),
			true
		));
	}

	@Test
	@DisplayName("При intake с валидным request_id связывает submission и делегирует завершение llm request lifecycle сервису")
	void acceptWithRequestIdLinksSubmissionAndCompletesLlmRequest() {
		UserFinanceIntakeOrchestrationService orchestrationService = orchestrationService();
		UserFinanceIntakeRequest request = validRequest("req-2026-03-15-member-anna");
		FinanceSubmissionEntity submission = submission();
		LlmCollectionRequestEntity llmCollectionRequest = llmCollectionRequest();

		given(llmCollectionRequestLifecycleService.resolveAcceptedForIntake("req-2026-03-15-member-anna"))
			.willReturn(llmCollectionRequest);
		given(intakeSubmissionService.saveSubmission(request, llmCollectionRequest)).willReturn(submission);

		orchestrationService.accept(request);

		verify(intakeSubmissionService).ensureExternalSubmissionIdIsNew("n8n-run-2026-03-15-001");
		verify(llmCollectionRequestLifecycleService).resolveAcceptedForIntake("req-2026-03-15-member-anna");
		verify(intakeSubmissionService).saveSubmission(request, llmCollectionRequest);
		verify(memberFinanceSnapshotRecalculationService).recalculateForMemberPeriod(MEMBER_ID, 2026, (short) 3);
		verify(familyDashboardSnapshotRecalculationService).recalculateForFamilyPeriod(FAMILY_ID, 2026, (short) 3);
		verify(llmCollectionRequestLifecycleService).markCompleted(llmCollectionRequest);
	}

	@Test
	@DisplayName("Если request_id не найден, возвращает validation error и не сохраняет submission")
	void acceptThrowsValidationErrorWhenRequestIdDoesNotExist() {
		UserFinanceIntakeOrchestrationService orchestrationService = orchestrationService();
		UserFinanceIntakeRequest request = validRequest("req-2026-03-15-member-anna");

		given(llmCollectionRequestLifecycleService.resolveAcceptedForIntake("req-2026-03-15-member-anna"))
			.willThrow(new LlmCollectionRequestNotFoundException("req-2026-03-15-member-anna"));

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> orchestrationService.accept(request),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("request_id", "llm collection request does not exist"));
		verify(intakeSubmissionService, never()).saveSubmission(any(), any());
		verify(memberFinanceSnapshotRecalculationService, never()).recalculateForMemberPeriod(any(), anyInt(), anyShort());
		verify(familyDashboardSnapshotRecalculationService, never()).recalculateForFamilyPeriod(any(), anyInt(), anyShort());
	}

	@Test
	@DisplayName("Если request_id указывает на запрос не в accepted статусе, возвращает validation error")
	void acceptThrowsValidationErrorWhenRequestIsNotAccepted() {
		UserFinanceIntakeOrchestrationService orchestrationService = orchestrationService();
		UserFinanceIntakeRequest request = validRequest("req-2026-03-15-member-anna");

		given(llmCollectionRequestLifecycleService.resolveAcceptedForIntake("req-2026-03-15-member-anna"))
			.willThrow(new LlmCollectionRequestInvalidStateException(
				"req-2026-03-15-member-anna",
				LlmCollectionRequestStatus.PENDING,
				LlmCollectionRequestStatus.ACCEPTED
			));

		InvalidIntakePayloadReferenceException exception = catchThrowableOfType(
			() -> orchestrationService.accept(request),
			InvalidIntakePayloadReferenceException.class
		);

		assertThat(exception).isNotNull();
		assertThat(exception.getDetails())
			.extracting(ValidationErrorDetail::field, ValidationErrorDetail::message)
			.containsExactly(tuple("request_id", "llm collection request must be in accepted status"));
		verify(intakeSubmissionService, never()).saveSubmission(any(), any());
		verify(memberFinanceSnapshotRecalculationService, never()).recalculateForMemberPeriod(any(), anyInt(), anyShort());
		verify(familyDashboardSnapshotRecalculationService, never()).recalculateForFamilyPeriod(any(), anyInt(), anyShort());
	}

	@Test
	@DisplayName("Если пересчет падает, orchestration не завершает llm request")
	void acceptDoesNotCompleteLlmRequestWhenRecalculationFails() {
		UserFinanceIntakeOrchestrationService orchestrationService = orchestrationService();
		UserFinanceIntakeRequest request = validRequest("req-2026-03-15-member-anna");
		FinanceSubmissionEntity submission = submission();
		LlmCollectionRequestEntity llmCollectionRequest = llmCollectionRequest();

		given(llmCollectionRequestLifecycleService.resolveAcceptedForIntake("req-2026-03-15-member-anna"))
			.willReturn(llmCollectionRequest);
		given(intakeSubmissionService.saveSubmission(request, llmCollectionRequest)).willReturn(submission);
		doThrow(new IllegalStateException("member snapshot failed"))
			.when(memberFinanceSnapshotRecalculationService)
			.recalculateForMemberPeriod(MEMBER_ID, 2026, (short) 3);

		assertThatThrownBy(() -> orchestrationService.accept(request))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("member snapshot failed");

		verify(familyDashboardSnapshotRecalculationService, never()).recalculateForFamilyPeriod(any(), anyInt(), anyShort());
		verify(llmCollectionRequestLifecycleService, never()).markCompleted(any());
	}

	private UserFinanceIntakeOrchestrationService orchestrationService() {
		return new UserFinanceIntakeOrchestrationService(
			intakeSubmissionService,
			memberFinanceSnapshotRecalculationService,
			familyDashboardSnapshotRecalculationService,
			llmCollectionRequestLifecycleService
		);
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

	private FinanceSubmissionEntity submission() {
		FamilyEntity family = new FamilyEntity();
		family.setId(FAMILY_ID);

		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setId(MEMBER_ID);
		member.setFamily(family);

		FinanceSubmissionEntity submission = new FinanceSubmissionEntity();
		submission.setId(SUBMISSION_ID);
		submission.setFamily(family);
		submission.setMember(member);
		submission.setPeriodYear(2026);
		submission.setPeriodMonth((short) 3);
		return submission;
	}

	private LlmCollectionRequestEntity llmCollectionRequest() {
		LlmCollectionRequestEntity llmCollectionRequest = new LlmCollectionRequestEntity();
		llmCollectionRequest.setRequestId("req-2026-03-15-member-anna");
		llmCollectionRequest.setStatus(LlmCollectionRequestStatus.ACCEPTED);
		return llmCollectionRequest;
	}
}
