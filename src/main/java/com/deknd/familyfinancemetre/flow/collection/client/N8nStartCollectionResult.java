package com.deknd.familyfinancemetre.flow.collection.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Результат запуска intake workflow в {@code n8n}.
 */
public sealed interface N8nStartCollectionResult permits N8nStartCollectionResult.Accepted, N8nStartCollectionResult.Failed {

	/**
	 * Успешный ответ {@code n8n} с подтверждением запуска workflow.
	 *
	 * @param status статус, возвращенный {@code n8n}; для MVP ожидается значение {@code accepted}
	 * @param requestId correlation id, возвращенный {@code n8n}
	 * @param workflowRunId идентификатор workflow run на стороне {@code n8n}
	 * @param responsePayload исходный JSON payload ответа {@code n8n}
	 */
	record Accepted(
		String status,
		String requestId,
		String workflowRunId,
		JsonNode responsePayload
	) implements N8nStartCollectionResult {
	}

	/**
	 * Неуспешный результат вызова {@code n8n}.
	 *
	 * @param httpStatus HTTP-статус ответа, если сервер успел его вернуть; иначе {@code null}
	 * @param responsePayload исходный JSON payload ошибки, если тело ответа удалось распарсить как JSON
	 * @param errorMessage понятное текстовое описание причины неуспешного вызова
	 */
	record Failed(
		Integer httpStatus,
		JsonNode responsePayload,
		String errorMessage
	) implements N8nStartCollectionResult {
	}
}
