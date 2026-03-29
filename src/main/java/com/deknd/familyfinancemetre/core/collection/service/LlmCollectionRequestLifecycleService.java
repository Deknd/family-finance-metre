package com.deknd.familyfinancemetre.core.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.collection.exception.LlmCollectionRequestInvalidStateException;
import com.deknd.familyfinancemetre.core.collection.exception.LlmCollectionRequestNotFoundException;
import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

/**
 * Управляет server-side lifecycle статусов {@code llm_collection_requests}.
 */
@Service
@RequiredArgsConstructor
public class LlmCollectionRequestLifecycleService {

	private final LlmCollectionRequestRepository llmCollectionRequestRepository;
	private final Clock clock;

	/**
	 * Переводит запрос из {@code pending} в {@code accepted} после успешного ответа {@code n8n}.
	 *
	 * @param request обновляемый запрос сбора данных
	 * @param workflowRunId идентификатор workflow run на стороне {@code n8n}
	 * @param responsePayload raw JSON payload успешного ответа {@code n8n}
	 * @return сохраненная запись со статусом {@code accepted}
	 */
	@Transactional
	public LlmCollectionRequestEntity markAccepted(
		LlmCollectionRequestEntity request,
		String workflowRunId,
		JsonNode responsePayload
	) {
		LlmCollectionRequestEntity requestToUpdate = requireRequest(request);
		requireNonBlank(workflowRunId, "Workflow run id must not be blank");
		Objects.requireNonNull(responsePayload, "Response payload must not be null");
		ensureCurrentStatus(requestToUpdate, LlmCollectionRequestStatus.PENDING);

		requestToUpdate.setStatus(LlmCollectionRequestStatus.ACCEPTED);
		requestToUpdate.setAcceptedAt(OffsetDateTime.now(clock));
		requestToUpdate.setWorkflowRunId(workflowRunId);
		requestToUpdate.setResponsePayload(responsePayload);
		requestToUpdate.setErrorMessage(null);
		return llmCollectionRequestRepository.saveAndFlush(requestToUpdate);
	}

	/**
	 * Переводит запрос из {@code pending} в {@code failed} и сохраняет данные ошибки вызова {@code n8n}.
	 *
	 * @param request обновляемый запрос сбора данных
	 * @param responsePayload raw JSON payload ошибки, если он есть
	 * @param errorMessage человекочитаемое описание причины ошибки
	 * @return сохраненная запись со статусом {@code failed}
	 */
	@Transactional
	public LlmCollectionRequestEntity markFailed(
		LlmCollectionRequestEntity request,
		JsonNode responsePayload,
		String errorMessage
	) {
		LlmCollectionRequestEntity requestToUpdate = requireRequest(request);
		requireNonBlank(errorMessage, "Error message must not be blank");
		ensureCurrentStatus(requestToUpdate, LlmCollectionRequestStatus.PENDING);

		requestToUpdate.setStatus(LlmCollectionRequestStatus.FAILED);
		requestToUpdate.setWorkflowRunId(null);
		requestToUpdate.setResponsePayload(responsePayload);
		requestToUpdate.setErrorMessage(errorMessage);
		return llmCollectionRequestRepository.saveAndFlush(requestToUpdate);
	}

	/**
	 * Переводит запрос из {@code accepted} в {@code completed} после успешного intake callback.
	 *
	 * @param request обновляемый запрос сбора данных
	 * @return сохраненная запись со статусом {@code completed}
	 */
	@Transactional
	public LlmCollectionRequestEntity markCompleted(LlmCollectionRequestEntity request) {
		LlmCollectionRequestEntity requestToUpdate = requireRequest(request);
		ensureCurrentStatus(requestToUpdate, LlmCollectionRequestStatus.ACCEPTED);

		requestToUpdate.setStatus(LlmCollectionRequestStatus.COMPLETED);
		requestToUpdate.setCompletedAt(OffsetDateTime.now(clock));
		return llmCollectionRequestRepository.saveAndFlush(requestToUpdate);
	}

	/**
	 * Находит запрос по {@code request_id} и подтверждает, что intake может завершить его из статуса {@code accepted}.
	 *
	 * @param requestId correlation id исходного server-side запуска
	 * @return найденный запрос в статусе {@code accepted}
	 */
	@Transactional(readOnly = true)
	public LlmCollectionRequestEntity resolveAcceptedForIntake(String requestId) {
		requireNonBlank(requestId, "Request id must not be blank");

		LlmCollectionRequestEntity request = llmCollectionRequestRepository.findByRequestId(requestId)
			.orElseThrow(() -> new LlmCollectionRequestNotFoundException(requestId));
		ensureCurrentStatus(request, LlmCollectionRequestStatus.ACCEPTED);
		return request;
	}

	private LlmCollectionRequestEntity requireRequest(LlmCollectionRequestEntity request) {
		return Objects.requireNonNull(request, "LLM collection request must not be null");
	}

	private void ensureCurrentStatus(
		LlmCollectionRequestEntity request,
		LlmCollectionRequestStatus... allowedStatuses
	) {
		LlmCollectionRequestStatus currentStatus = request.getStatus();
		boolean statusAllowed = Arrays.stream(allowedStatuses).anyMatch(allowedStatus -> allowedStatus == currentStatus);
		if (!statusAllowed) {
			throw new LlmCollectionRequestInvalidStateException(
				request.getRequestId(),
				currentStatus,
				allowedStatuses
			);
		}
	}

	private void requireNonBlank(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}
}
