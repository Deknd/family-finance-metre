package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.service.LlmCollectionRequestLifecycleService;
import com.deknd.familyfinancemetre.flow.collection.client.N8nClient;
import com.deknd.familyfinancemetre.flow.collection.client.N8nStartCollectionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Оркестрирует outbound-вызов {@code n8n} для уже созданного {@code pending} запроса.
 */
@Service
@RequiredArgsConstructor
public class LlmCollectionRequestDispatchService {

	private final N8nClient n8nClient;
	private final LlmCollectionRequestLifecycleService llmCollectionRequestLifecycleService;

	/**
	 * Отправляет сохраненный request payload в {@code n8n} и фиксирует итог lifecycle-перехода.
	 *
	 * @param request ранее созданный {@code pending} запрос сбора данных
	 * @return результат вызова webhook {@code n8n}
	 */
	public N8nStartCollectionResult dispatchPendingRequest(LlmCollectionRequestEntity request) {
		LlmCollectionRequestEntity requestToDispatch = Objects.requireNonNull(
			request,
			"LLM collection request must not be null"
		);

		N8nStartCollectionResult result = n8nClient.startCollection(
			requestToDispatch.getRequestId(),
			requestToDispatch.getRequestPayload()
		);

		if (result instanceof N8nStartCollectionResult.Accepted accepted) {
			llmCollectionRequestLifecycleService.markAccepted(
				requestToDispatch,
				accepted.workflowRunId(),
				accepted.responsePayload()
			);
			return result;
		}

		N8nStartCollectionResult.Failed failed = (N8nStartCollectionResult.Failed) result;
		llmCollectionRequestLifecycleService.markFailed(
			requestToDispatch,
			failed.responsePayload(),
			failed.errorMessage()
		);
		return result;
	}
}
