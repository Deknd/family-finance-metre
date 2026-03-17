package com.deknd.familyfinancemetre.controller;

import com.deknd.familyfinancemetre.dto.validation.ValidationErrorResponse;
import com.deknd.familyfinancemetre.dto.validation.ValidationErrorResponse.ValidationErrorDetail;
import com.deknd.familyfinancemetre.entity.enums.DatabaseEnum;
import com.deknd.familyfinancemetre.exception.DuplicateSubmissionException;
import com.deknd.familyfinancemetre.exception.InvalidIntakePayloadReferenceException;
import com.deknd.familyfinancemetre.security.ApiErrorResponse;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiValidationExceptionHandler {

	private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
	private static final String VALIDATION_ERROR_MESSAGE = "Request validation failed";
	private static final Pattern MESSAGE_PATH_PATTERN = Pattern.compile("\\[\"([^\"]+)\"]");

	/**
	 * Возвращает ошибки bean validation для невалидного request body.
	 *
	 * @param exception исключение валидации аргумента контроллера
	 * @return ответ с деталями ошибок валидации
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		List<ValidationErrorDetail> details = exception.getBindingResult().getAllErrors().stream()
			.map(this::toValidationErrorDetail)
			.sorted(Comparator.comparing(ValidationErrorDetail::field).thenComparing(ValidationErrorDetail::message))
			.toList();

		return buildValidationErrorResponse(details);
	}

	/**
	 * Возвращает ошибки биндинга query/path параметров в едином формате валидации.
	 *
	 * @param exception исключение биндинга входных данных запроса
	 * @return ответ с деталями ошибок валидации
	 */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ValidationErrorResponse> handleBindException(BindException exception) {
		List<ValidationErrorDetail> details = exception.getBindingResult().getAllErrors().stream()
			.map(this::toValidationErrorDetail)
			.sorted(Comparator.comparing(ValidationErrorDetail::field).thenComparing(ValidationErrorDetail::message))
			.toList();

		return buildValidationErrorResponse(details);
	}

	/**
	 * Возвращает читаемую ошибку, если тело запроса не удалось разобрать.
	 *
	 * @param exception исключение разбора JSON тела запроса
	 * @return ответ с одной деталью ошибки чтения тела запроса
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ValidationErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
		return buildValidationErrorResponse(List.of(extractReadableErrorDetail(exception)));
	}

	/**
	 * Возвращает ошибку конфликта при повторной отправке уже обработанного payload.
	 *
	 * @param exception исключение о дублирующейся отправке
	 * @return ответ с кодом конфликта и описанием ошибки
	 */
	@ExceptionHandler(DuplicateSubmissionException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateSubmission(DuplicateSubmissionException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiErrorResponse.of(DuplicateSubmissionException.ERROR_CODE, exception.getMessage()));
	}

	/**
	 * Возвращает доменные ошибки валидации ссылок intake payload.
	 *
	 * @param exception исключение с деталями невалидных ссылок payload
	 * @return ответ с деталями доменной валидации intake payload
	 */
	@ExceptionHandler(InvalidIntakePayloadReferenceException.class)
	public ResponseEntity<ValidationErrorResponse> handleInvalidIntakePayloadReference(
		InvalidIntakePayloadReferenceException exception
	) {
		return buildValidationErrorResponse(exception.getDetails());
	}

	private ResponseEntity<ValidationErrorResponse> buildValidationErrorResponse(List<ValidationErrorDetail> details) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
			.body(ValidationErrorResponse.of(VALIDATION_ERROR_CODE, VALIDATION_ERROR_MESSAGE, details));
	}

	private ValidationErrorDetail toValidationErrorDetail(ObjectError error) {
		if (error instanceof FieldError fieldError) {
			return new ValidationErrorDetail(toSnakeCasePath(fieldError.getField()), fieldError.getDefaultMessage());
		}

		return new ValidationErrorDetail(toSnakeCasePath(error.getObjectName()), error.getDefaultMessage());
	}

	private ValidationErrorDetail extractReadableErrorDetail(HttpMessageNotReadableException exception) {
		InvalidFormatException invalidFormatException = findCause(exception, InvalidFormatException.class);
		if (invalidFormatException != null) {
			return extractInvalidFormatDetail(invalidFormatException);
		}
		MismatchedInputException mismatchedInputException = findCause(exception, MismatchedInputException.class);
		if (mismatchedInputException != null) {
			return buildReadableDetail(extractPath(mismatchedInputException), mismatchedInputException.getOriginalMessage());
		}
		JsonMappingException jsonMappingException = findCause(exception, JsonMappingException.class);
		if (jsonMappingException != null) {
			return buildReadableDetail(extractPath(jsonMappingException), jsonMappingException.getOriginalMessage());
		}
		JsonParseException jsonParseException = findCause(exception, JsonParseException.class);
		if (jsonParseException != null) {
			return new ValidationErrorDetail("body", "Malformed JSON request");
		}

		return buildReadableDetail("", exception.getMessage());
	}

	private ValidationErrorDetail extractInvalidFormatDetail(InvalidFormatException exception) {
		String field = extractPath(exception);
		String resolvedField = field.isBlank() ? "body" : field;
		Class<?> targetType = exception.getTargetType();
		TaggedMessage taggedMessage = extractTaggedMessage(exception.getOriginalMessage());

		if (taggedMessage != null) {
			return new ValidationErrorDetail(
				taggedMessage.field().isBlank() ? resolvedField : taggedMessage.field(),
				taggedMessage.message()
			);
		}

		if (targetType == UUID.class) {
			return new ValidationErrorDetail(resolvedField, "must be a valid UUID");
		}
		if (targetType == OffsetDateTime.class) {
			return new ValidationErrorDetail(resolvedField, "must be a valid ISO-8601 date-time with offset");
		}
		if (targetType != null && targetType.isEnum()) {
			return new ValidationErrorDetail(resolvedField, "must be one of: " + extractAllowedEnumValues(targetType));
		}
		if (targetType != null && (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive())) {
			return new ValidationErrorDetail(resolvedField, "must be a valid number");
		}

		return new ValidationErrorDetail(resolvedField, "value has invalid format");
	}

	private TaggedMessage extractTaggedMessage(String message) {
		if (message == null || !message.startsWith("FIELD:")) {
			return null;
		}

		int separatorIndex = message.indexOf('|');
		if (separatorIndex < 0) {
			return null;
		}

		String field = message.substring("FIELD:".length(), separatorIndex);
		String validationMessage = message.substring(separatorIndex + 1);
		return new TaggedMessage(field, validationMessage);
	}

	private ValidationErrorDetail buildReadableDetail(String field, String rawMessage) {
		String resolvedField = field == null || field.isBlank() ? extractPathFromMessage(rawMessage) : field;
		String message = rawMessage == null ? "" : rawMessage;

		if (message.contains("java.util.UUID")) {
			return new ValidationErrorDetail(defaultField(resolvedField), "must be a valid UUID");
		}
		if (message.contains("java.time.OffsetDateTime")) {
			return new ValidationErrorDetail(defaultField(resolvedField), "must be a valid ISO-8601 date-time with offset");
		}
		if ("source".equals(resolvedField)) {
			return new ValidationErrorDetail(resolvedField, "must be one of: telegram");
		}
		if ("meta.confidence".equals(resolvedField)) {
			return new ValidationErrorDetail(resolvedField, "must be one of: low, medium, high");
		}
		if (message.contains("Unexpected end-of-input") || message.contains("Unexpected character")) {
			return new ValidationErrorDetail("body", "Malformed JSON request");
		}

		return new ValidationErrorDetail(defaultField(resolvedField), "value has invalid format");
	}

	private String extractAllowedEnumValues(Class<?> targetType) {
		return Arrays.stream(targetType.getEnumConstants())
			.map(enumConstant -> enumConstant instanceof DatabaseEnum databaseEnum
				? databaseEnum.getDatabaseValue()
				: enumConstant.toString().toLowerCase(Locale.ROOT))
			.collect(Collectors.joining(", "));
	}

	private String extractPath(JsonMappingException exception) {
		String path = toSnakeCasePath(exception.getPath().stream()
			.map(reference -> reference.getFieldName())
			.filter(fieldName -> fieldName != null && !fieldName.isBlank())
			.collect(Collectors.joining(".")));

		if (!path.isBlank()) {
			return path;
		}

		return extractPathFromMessage(exception.getPathReference());
	}

	private String extractPathFromMessage(String message) {
		if (message == null || message.isBlank()) {
			return "";
		}

		Matcher matcher = MESSAGE_PATH_PATTERN.matcher(message);
		StringBuilder path = new StringBuilder();
		while (matcher.find()) {
			if (!path.isEmpty()) {
				path.append('.');
			}
			path.append(matcher.group(1));
		}

		return toSnakeCasePath(path.toString());
	}

	private String defaultField(String field) {
		return field == null || field.isBlank() ? "body" : field;
	}

	private <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
		Throwable current = throwable;
		while (current != null) {
			if (causeType.isInstance(current)) {
				return causeType.cast(current);
			}
			current = current.getCause();
		}
		return null;
	}

	private record TaggedMessage(
		String field,
		String message
	) {
	}

	private String toSnakeCasePath(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}

		return Arrays.stream(path.split("\\."))
			.map(this::toSnakeCase)
			.collect(Collectors.joining("."));
	}

	private String toSnakeCase(String value) {
		StringBuilder result = new StringBuilder(value.length() + 8);
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (Character.isUpperCase(current)) {
				if (index > 0) {
					result.append('_');
				}
				result.append(Character.toLowerCase(current));
				continue;
			}
			result.append(current);
		}
		return result.toString();
	}
}
