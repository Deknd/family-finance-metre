package com.deknd.familyfinancemetre.flow.collection.client;

import com.deknd.familyfinancemetre.shared.config.ApplicationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Objects;

/**
 * Реализация HTTP-клиента запуска intake workflow в {@code n8n} через {@link RestClient}.
 */
@Component
public class RestN8nClient implements N8nClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final URI webhookUrl;

	RestN8nClient(
		@Qualifier("n8nRestClient") RestClient restClient,
		@Qualifier("n8nClientObjectMapper") ObjectMapper objectMapper,
		ApplicationProperties applicationProperties
	) {
		this.restClient = Objects.requireNonNull(restClient, "RestClient must not be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
		this.webhookUrl = Objects.requireNonNull(
			applicationProperties,
			"Application properties must not be null"
		).integrations().n8n().webhookUrl();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public N8nStartCollectionResult startCollection(String requestId, JsonNode requestPayload) {
		Objects.requireNonNull(requestId, "Request id must not be null");
		Objects.requireNonNull(requestPayload, "Request payload must not be null");

		try {
			String requestBody = objectMapper.writeValueAsString(requestPayload);
			HttpExchangeResponse response = executeRequest(requestBody);
			JsonNode responsePayload = parseJsonSafely(response.body());

			if (response.statusCode() != HttpStatus.ACCEPTED.value()) {
				return new N8nStartCollectionResult.Failed(
					response.statusCode(),
					responsePayload,
					buildHttpFailureMessage(response.statusCode(), responsePayload, response.body())
				);
			}

			return mapAcceptedResponse(requestId, responsePayload, response.body());
		} catch (JsonProcessingException exception) {
			return new N8nStartCollectionResult.Failed(
				null,
				null,
				"Не удалось сериализовать request_payload для запуска n8n: " + exception.getOriginalMessage()
			);
		} catch (RestClientException exception) {
			return new N8nStartCollectionResult.Failed(
				null,
				null,
				"Не удалось вызвать webhook n8n: " + resolveExceptionMessage(exception)
			);
		}
	}

	private HttpExchangeResponse executeRequest(String requestBody) {
		return restClient.post()
			.uri(webhookUrl)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.body(requestBody)
			.exchange((request, response) -> new HttpExchangeResponse(
				response.getStatusCode().value(),
				normalizeBody(response.bodyTo(String.class))
			));
	}

	private N8nStartCollectionResult mapAcceptedResponse(
		String requestId,
		JsonNode responsePayload,
		String rawBody
	) {
		if (!StringUtils.hasText(rawBody)) {
			return new N8nStartCollectionResult.Failed(
				HttpStatus.ACCEPTED.value(),
				responsePayload,
				"n8n вернул 202 Accepted без тела ответа"
			);
		}

		if (responsePayload == null || !responsePayload.isObject()) {
			return new N8nStartCollectionResult.Failed(
				HttpStatus.ACCEPTED.value(),
				responsePayload,
				"n8n вернул 202 Accepted с телом, которое не удалось распарсить как JSON-объект"
			);
		}

		String status = readNullableText(responsePayload, "status");
		if (!"accepted".equals(status)) {
			return new N8nStartCollectionResult.Failed(
				HttpStatus.ACCEPTED.value(),
				responsePayload,
				"n8n вернул 202 Accepted с некорректным статусом ответа: " + status
			);
		}

		String responseRequestId = readNullableText(responsePayload, "request_id");
		if (!requestId.equals(responseRequestId)) {
			return new N8nStartCollectionResult.Failed(
				HttpStatus.ACCEPTED.value(),
				responsePayload,
				"n8n вернул request_id, который не совпадает с отправленным correlation id"
			);
		}

		String workflowRunId = readNullableText(responsePayload, "workflow_run_id");
		if (!StringUtils.hasText(workflowRunId)) {
			return new N8nStartCollectionResult.Failed(
				HttpStatus.ACCEPTED.value(),
				responsePayload,
				"n8n вернул 202 Accepted без workflow_run_id"
			);
		}

		return new N8nStartCollectionResult.Accepted(
			status,
			responseRequestId,
			workflowRunId,
			responsePayload
		);
	}

	private JsonNode parseJsonSafely(String body) {
		if (!StringUtils.hasText(body)) {
			return null;
		}

		try {
			return objectMapper.readTree(body);
		} catch (JsonProcessingException exception) {
			return null;
		}
	}

	private String buildHttpFailureMessage(
		int statusCode,
		JsonNode responsePayload,
		String rawBody
	) {
		String detailedMessage = extractMessage(responsePayload);
		if (!StringUtils.hasText(detailedMessage)) {
			detailedMessage = rawBody;
		}

		if (StringUtils.hasText(detailedMessage)) {
			return "n8n вернул HTTP " + statusCode + ": " + detailedMessage;
		}

		return "n8n вернул HTTP " + statusCode + " при запуске intake workflow";
	}

	private String extractMessage(JsonNode responsePayload) {
		if (responsePayload == null || !responsePayload.isObject()) {
			return null;
		}

		String nestedErrorMessage = readNullableText(responsePayload.path("error"), "message");
		if (StringUtils.hasText(nestedErrorMessage)) {
			return nestedErrorMessage;
		}

		return readNullableText(responsePayload, "message");
	}

	private String readNullableText(JsonNode payload, String fieldName) {
		if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
			return null;
		}
		String value = payload.get(fieldName).asText();
		return StringUtils.hasText(value) ? value : null;
	}

	private String resolveExceptionMessage(RestClientException exception) {
		Throwable mostSpecificCause = exception.getMostSpecificCause();
		if (StringUtils.hasText(mostSpecificCause.getMessage())) {
			return mostSpecificCause.getMessage();
		}
		return exception.getMessage();
	}

	private String normalizeBody(String body) {
		return body == null ? "" : body;
	}

	private record HttpExchangeResponse(
		int statusCode,
		String body
	) {
	}
}
