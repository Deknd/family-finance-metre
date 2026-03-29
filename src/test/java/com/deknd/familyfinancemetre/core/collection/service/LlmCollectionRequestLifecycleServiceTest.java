package com.deknd.familyfinancemetre.core.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.collection.exception.LlmCollectionRequestInvalidStateException;
import com.deknd.familyfinancemetre.core.collection.exception.LlmCollectionRequestNotFoundException;
import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LlmCollectionRequestLifecycleServiceTest {

	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
	private static final Clock FIXED_CLOCK = Clock.fixed(
		Instant.parse("2026-03-20T07:45:00Z"),
		ZoneId.of("Europe/Moscow")
	);

	@Mock
	private LlmCollectionRequestRepository llmCollectionRequestRepository;

	private LlmCollectionRequestLifecycleService lifecycleService;

	@BeforeEach
	void setUp() {
		lifecycleService = new LlmCollectionRequestLifecycleService(llmCollectionRequestRepository, FIXED_CLOCK);
	}

	@Test
	@DisplayName("Переводит pending запрос в accepted и сохраняет данные ответа n8n")
	void markAcceptedMovesPendingRequestToAccepted() throws Exception {
		LlmCollectionRequestEntity request = requestWithStatus(LlmCollectionRequestStatus.PENDING);
		given(llmCollectionRequestRepository.saveAndFlush(any(LlmCollectionRequestEntity.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		lifecycleService.markAccepted(
			request,
			"n8n-run-001",
			OBJECT_MAPPER.readTree("""
				{
				  "status": "accepted",
				  "request_id": "99999999-9999-9999-9999-999999999999",
				  "workflow_run_id": "n8n-run-001"
				}
				""")
		);

		assertThat(request.getStatus()).isEqualTo(LlmCollectionRequestStatus.ACCEPTED);
		assertThat(request.getAcceptedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
		assertThat(request.getWorkflowRunId()).isEqualTo("n8n-run-001");
		assertThat(request.getResponsePayload().at("/status").asText()).isEqualTo("accepted");
		assertThat(request.getErrorMessage()).isNull();
		verify(llmCollectionRequestRepository).saveAndFlush(request);
	}

	@Test
	@DisplayName("Переводит pending запрос в failed и сохраняет описание ошибки")
	void markFailedMovesPendingRequestToFailed() throws Exception {
		LlmCollectionRequestEntity request = requestWithStatus(LlmCollectionRequestStatus.PENDING);
		given(llmCollectionRequestRepository.saveAndFlush(any(LlmCollectionRequestEntity.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		lifecycleService.markFailed(
			request,
			OBJECT_MAPPER.readTree("""
				{
				  "error": {
				    "code": "INVALID_TOKEN"
				  }
				}
				"""),
			"n8n вернул HTTP 401: Authorization shared secret is invalid"
		);

		assertThat(request.getStatus()).isEqualTo(LlmCollectionRequestStatus.FAILED);
		assertThat(request.getWorkflowRunId()).isNull();
		assertThat(request.getResponsePayload().at("/error/code").asText()).isEqualTo("INVALID_TOKEN");
		assertThat(request.getErrorMessage()).isEqualTo("n8n вернул HTTP 401: Authorization shared secret is invalid");
		verify(llmCollectionRequestRepository).saveAndFlush(request);
	}

	@Test
	@DisplayName("Переводит accepted запрос в completed после успешного intake callback")
	void markCompletedMovesAcceptedRequestToCompleted() {
		LlmCollectionRequestEntity request = requestWithStatus(LlmCollectionRequestStatus.ACCEPTED);
		given(llmCollectionRequestRepository.saveAndFlush(any(LlmCollectionRequestEntity.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		lifecycleService.markCompleted(request);

		assertThat(request.getStatus()).isEqualTo(LlmCollectionRequestStatus.COMPLETED);
		assertThat(request.getCompletedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
		verify(llmCollectionRequestRepository).saveAndFlush(request);
	}

	@Test
	@DisplayName("Запрещает недопустимый переход pending в completed")
	void markCompletedRejectsNonAcceptedRequest() {
		LlmCollectionRequestEntity request = requestWithStatus(LlmCollectionRequestStatus.PENDING);

		assertThatThrownBy(() -> lifecycleService.markCompleted(request))
			.isInstanceOf(LlmCollectionRequestInvalidStateException.class)
			.hasMessageContaining("expected one of: accepted");
	}

	@Test
	@DisplayName("Возвращает accepted запрос для intake по request_id")
	void resolveAcceptedForIntakeReturnsAcceptedRequest() {
		LlmCollectionRequestEntity request = requestWithStatus(LlmCollectionRequestStatus.ACCEPTED);
		given(llmCollectionRequestRepository.findByRequestId("99999999-9999-9999-9999-999999999999"))
			.willReturn(Optional.of(request));

		LlmCollectionRequestEntity resolvedRequest = lifecycleService.resolveAcceptedForIntake(
			"99999999-9999-9999-9999-999999999999"
		);

		assertThat(resolvedRequest).isSameAs(request);
	}

	@Test
	@DisplayName("Отклоняет intake request_id, если запрос не найден")
	void resolveAcceptedForIntakeThrowsWhenRequestDoesNotExist() {
		given(llmCollectionRequestRepository.findByRequestId("99999999-9999-9999-9999-999999999999"))
			.willReturn(Optional.empty());

		assertThatThrownBy(() -> lifecycleService.resolveAcceptedForIntake("99999999-9999-9999-9999-999999999999"))
			.isInstanceOf(LlmCollectionRequestNotFoundException.class);
	}

	@Test
	@DisplayName("Отклоняет intake request_id, если запрос еще не в accepted статусе")
	void resolveAcceptedForIntakeThrowsWhenRequestIsNotAccepted() {
		LlmCollectionRequestEntity request = requestWithStatus(LlmCollectionRequestStatus.PENDING);
		given(llmCollectionRequestRepository.findByRequestId("99999999-9999-9999-9999-999999999999"))
			.willReturn(Optional.of(request));

		assertThatThrownBy(() -> lifecycleService.resolveAcceptedForIntake("99999999-9999-9999-9999-999999999999"))
			.isInstanceOf(LlmCollectionRequestInvalidStateException.class)
			.hasMessageContaining("expected one of: accepted");
	}

	private LlmCollectionRequestEntity requestWithStatus(LlmCollectionRequestStatus status) {
		LlmCollectionRequestEntity request = new LlmCollectionRequestEntity();
		request.setRequestId("99999999-9999-9999-9999-999999999999");
		request.setStatus(status);
		return request;
	}
}
