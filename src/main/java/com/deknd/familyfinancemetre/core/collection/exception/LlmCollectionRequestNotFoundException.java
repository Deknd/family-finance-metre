package com.deknd.familyfinancemetre.core.collection.exception;

/**
 * Сигнализирует, что запрос lifecycle не нашел запись {@code llm_collection_requests}.
 */
public class LlmCollectionRequestNotFoundException extends RuntimeException {

	/**
	 * Создает исключение для отсутствующего запроса по {@code request_id}.
	 *
	 * @param requestId correlation id, по которому не удалось найти запрос
	 */
	public LlmCollectionRequestNotFoundException(String requestId) {
		super("LLM collection request was not found by request_id: " + requestId);
	}
}
