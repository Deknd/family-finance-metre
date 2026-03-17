package com.deknd.familyfinancemetre.exception;

import com.deknd.familyfinancemetre.dto.validation.ValidationErrorResponse.ValidationErrorDetail;

import java.util.List;

public class InvalidIntakePayloadReferenceException extends RuntimeException {

	private final List<ValidationErrorDetail> details;

	/**
	 * Создает исключение доменной валидации intake payload с деталями по невалидным ссылкам.
	 *
	 * @param details список ошибок доменной валидации intake payload
	 */
	public InvalidIntakePayloadReferenceException(List<ValidationErrorDetail> details) {
		super("Request validation failed");
		this.details = List.copyOf(details);
	}

	/**
	 * Возвращает список ошибок доменной валидации intake payload.
	 *
	 * @return список ошибок по невалидным ссылкам payload
	 */
	public List<ValidationErrorDetail> getDetails() {
		return details;
	}
}
