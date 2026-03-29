package com.deknd.familyfinancemetre.flow.collection.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Запускает intake workflow в {@code n8n} по сохраненному request payload.
 */
public interface N8nClient {

	/**
	 * Отправляет в {@code n8n} ранее сохраненный payload запуска опроса.
	 *
	 * @param requestId correlation id запуска, который должен быть возвращен в ответе без изменений
	 * @param requestPayload JSON payload, ранее сохраненный в {@code llm_collection_requests.request_payload}
	 * @return структурированный результат HTTP-вызова с успешной или неуспешной веткой
	 */
	N8nStartCollectionResult startCollection(String requestId, JsonNode requestPayload);
}
