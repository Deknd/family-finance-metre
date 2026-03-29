package com.deknd.familyfinancemetre.flow.collection.client;

import com.deknd.familyfinancemetre.shared.config.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestN8nClientTest {

	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

	private HttpServer httpServer;

	@AfterEach
	void tearDown() {
		if (httpServer != null) {
			httpServer.stop(0);
		}
	}

	@Test
	@DisplayName("Отправляет POST в webhook n8n с bearer token и возвращает accepted результат")
	void startCollectionReturnsAcceptedResultForAcceptedWebhookResponse() throws Exception {
		AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
		JsonNode requestPayload = OBJECT_MAPPER.readTree(
			"""
				{
				  "request_id": "99999999-9999-9999-9999-999999999999",
				  "member": {
				    "id": "22222222-2222-2222-2222-222222222222"
				  }
				}
				"""
		);
		httpServer = startServer(exchange -> {
			capturedRequest.set(captureRequest(exchange));
			writeJsonResponse(
				exchange,
				202,
				"""
					{
					  "status": "accepted",
					  "request_id": "99999999-9999-9999-9999-999999999999",
					  "workflow_run_id": "n8n-run-001"
					}
					"""
			);
		});
		RestN8nClient client = client(httpServer);

		N8nStartCollectionResult result = client.startCollection(
			"99999999-9999-9999-9999-999999999999",
			requestPayload
		);

		assertThat(result).isInstanceOfSatisfying(N8nStartCollectionResult.Accepted.class, accepted -> {
			assertThat(accepted.status()).isEqualTo("accepted");
			assertThat(accepted.requestId()).isEqualTo("99999999-9999-9999-9999-999999999999");
			assertThat(accepted.workflowRunId()).isEqualTo("n8n-run-001");
			assertThat(accepted.responsePayload().at("/workflow_run_id").asText()).isEqualTo("n8n-run-001");
		});
		assertThat(capturedRequest.get()).satisfies(request -> {
			assertThat(request.method()).isEqualTo("POST");
			assertThat(request.path()).isEqualTo("/webhook/finance-intake-start");
			assertThat(request.authorization()).isEqualTo("Bearer bearer-token");
			assertThat(request.contentType()).startsWith("application/json");
			assertThat(request.accept()).contains("application/json");
			assertThat(request.body()).isEqualTo(OBJECT_MAPPER.writeValueAsString(requestPayload));
		});
	}

	@Test
	@DisplayName("Возвращает failed результат с HTTP статусом и raw JSON, если n8n ответил ошибкой")
	void startCollectionReturnsFailedResultForHttpErrorResponse() throws Exception {
		httpServer = startServer(exchange -> writeJsonResponse(
			exchange,
			401,
			"""
				{
				  "error": {
				    "code": "INVALID_TOKEN",
				    "message": "Authorization shared secret is invalid"
				  }
				}
				"""
		));
		RestN8nClient client = client(httpServer);

		N8nStartCollectionResult result = client.startCollection(
			"99999999-9999-9999-9999-999999999999",
			OBJECT_MAPPER.readTree("{\"request_id\":\"99999999-9999-9999-9999-999999999999\"}")
		);

		assertThat(result).isInstanceOfSatisfying(N8nStartCollectionResult.Failed.class, failed -> {
			assertThat(failed.httpStatus()).isEqualTo(401);
			assertThat(failed.responsePayload().at("/error/code").asText()).isEqualTo("INVALID_TOKEN");
			assertThat(failed.errorMessage())
				.contains("HTTP 401")
				.contains("Authorization shared secret is invalid");
		});
	}

	@Test
	@DisplayName("Возвращает failed результат, если в accepted ответе request_id не совпадает с отправленным")
	void startCollectionReturnsFailedResultForAcceptedResponseWithMismatchedRequestId() throws Exception {
		httpServer = startServer(exchange -> writeJsonResponse(
			exchange,
			202,
			"""
				{
				  "status": "accepted",
				  "request_id": "different-request-id",
				  "workflow_run_id": "n8n-run-001"
				}
				"""
		));
		RestN8nClient client = client(httpServer);

		N8nStartCollectionResult result = client.startCollection(
			"99999999-9999-9999-9999-999999999999",
			OBJECT_MAPPER.readTree("{\"request_id\":\"99999999-9999-9999-9999-999999999999\"}")
		);

		assertThat(result).isInstanceOfSatisfying(N8nStartCollectionResult.Failed.class, failed -> {
			assertThat(failed.httpStatus()).isEqualTo(202);
			assertThat(failed.responsePayload().at("/request_id").asText()).isEqualTo("different-request-id");
			assertThat(failed.errorMessage()).contains("request_id");
		});
	}

	@Test
	@DisplayName("Возвращает failed результат с текстом ошибки, если чтение ответа n8n завершилось по таймауту")
	void startCollectionReturnsFailedResultForReadTimeout() throws Exception {
		httpServer = startServer(exchange -> {
			try {
				Thread.sleep(300);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			writeJsonResponse(
				exchange,
				202,
				"""
					{
					  "status": "accepted",
					  "request_id": "99999999-9999-9999-9999-999999999999",
					  "workflow_run_id": "n8n-run-001"
					}
					"""
			);
		});
		RestN8nClient client = client(httpServer, Duration.ofMillis(200), Duration.ofMillis(50));

		N8nStartCollectionResult result = client.startCollection(
			"99999999-9999-9999-9999-999999999999",
			OBJECT_MAPPER.readTree("{\"request_id\":\"99999999-9999-9999-9999-999999999999\"}")
		);

		assertThat(result).isInstanceOfSatisfying(N8nStartCollectionResult.Failed.class, failed -> {
			assertThat(failed.httpStatus()).isNull();
			assertThat(failed.responsePayload()).isNull();
			assertThat(failed.errorMessage()).isNotBlank().contains("Не удалось вызвать webhook n8n");
		});
	}

	private HttpServer startServer(ExchangeHandler handler) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/webhook/finance-intake-start", exchange -> {
			try {
				handler.handle(exchange);
			} finally {
				exchange.close();
			}
		});
		server.start();
		return server;
	}

	private CapturedRequest captureRequest(HttpExchange exchange) throws IOException {
		return new CapturedRequest(
			exchange.getRequestMethod(),
			exchange.getRequestURI().getPath(),
			exchange.getRequestHeaders().getFirst("Authorization"),
			exchange.getRequestHeaders().getFirst("Content-Type"),
			exchange.getRequestHeaders().getFirst("Accept"),
			new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
		);
	}

	private void writeJsonResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
		byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, responseBytes.length);
		try (OutputStream responseStream = exchange.getResponseBody()) {
			responseStream.write(responseBytes);
		}
	}

	private ApplicationProperties applicationProperties(HttpServer server) {
		return applicationProperties(server, Duration.ofSeconds(1), Duration.ofSeconds(1));
	}

	private ApplicationProperties applicationProperties(
		HttpServer server,
		Duration connectTimeout,
		Duration readTimeout
	) {
		return new ApplicationProperties(
			ZoneId.of("Europe/Moscow"),
			new ApplicationProperties.Security("test-n8n-api-key"),
			new ApplicationProperties.Integrations(
				new ApplicationProperties.N8n(
					URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook/finance-intake-start"),
					"bearer-token",
					URI.create("https://server.example.com/api/v1/intake/user-finance-data"),
					connectTimeout,
					readTimeout
				)
			),
			new ApplicationProperties.Scheduler(
				new ApplicationProperties.PayrollCollection(true, "0 0 9 * * *")
			)
		);
	}

	private RestN8nClient client(HttpServer server) {
		return client(server, Duration.ofSeconds(1), Duration.ofSeconds(1));
	}

	private RestN8nClient client(
		HttpServer server,
		Duration connectTimeout,
		Duration readTimeout
	) {
		ApplicationProperties applicationProperties = applicationProperties(server, connectTimeout, readTimeout);
		N8nClientConfiguration configuration = new N8nClientConfiguration(applicationProperties);
		return new RestN8nClient(
			configuration.n8nRestClient(),
			configuration.n8nClientObjectMapper(),
			applicationProperties
		);
	}

	@FunctionalInterface
	private interface ExchangeHandler {

		void handle(HttpExchange exchange) throws IOException;
	}

	private record CapturedRequest(
		String method,
		String path,
		String authorization,
		String contentType,
		String accept,
		String body
	) {
	}
}
