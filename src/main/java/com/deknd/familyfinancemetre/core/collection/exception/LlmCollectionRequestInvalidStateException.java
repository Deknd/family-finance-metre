package com.deknd.familyfinancemetre.core.collection.exception;

import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Сигнализирует о недопустимом server-side переходе статуса запроса.
 */
public class LlmCollectionRequestInvalidStateException extends RuntimeException {

	/**
	 * Создает исключение для запроса с неподходящим текущим статусом.
	 *
	 * @param requestId correlation id запроса
	 * @param actualStatus фактический текущий статус записи
	 * @param allowedStatuses допустимые статусы, из которых разрешен переход
	 */
	public LlmCollectionRequestInvalidStateException(
		String requestId,
		LlmCollectionRequestStatus actualStatus,
		LlmCollectionRequestStatus... allowedStatuses
	) {
		super(
			"LLM collection request %s has invalid status %s, expected one of: %s"
				.formatted(requestId, actualStatus, formatAllowedStatuses(allowedStatuses))
		);
	}

	private static String formatAllowedStatuses(LlmCollectionRequestStatus[] allowedStatuses) {
		return Arrays.stream(allowedStatuses)
			.map(LlmCollectionRequestStatus::getDatabaseValue)
			.collect(Collectors.joining(", "));
	}
}
