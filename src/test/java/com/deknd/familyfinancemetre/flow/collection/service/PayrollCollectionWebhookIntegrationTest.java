package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("migration")
class PayrollCollectionWebhookIntegrationTest {

	private static final String API_KEY = "migration-n8n-api-key";
	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID PAYROLL_SCHEDULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
	private static final ZoneId APPLICATION_ZONE = ZoneId.of("Europe/Moscow");
	private static final AtomicReference<CapturedRequest> CAPTURED_REQUEST = new AtomicReference<>();
	private static final AtomicReference<ExchangeHandler> EXCHANGE_HANDLER = new AtomicReference<>();
	private static final HttpServer WEBHOOK_SERVER = startWebhookServer();

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_collection_webhook_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add(
			"app.integrations.n8n.webhook-url",
			() -> "http://127.0.0.1:" + WEBHOOK_SERVER.getAddress().getPort() + "/webhook/finance-intake-start"
		);
	}

	@Autowired
	private PayrollCollectionOrchestrationService payrollCollectionOrchestrationService;

	@Autowired
	private LlmCollectionRequestRepository llmCollectionRequestRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private Clock clock;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute(
			"truncate table family_dashboard_snapshots, member_finance_snapshots, finance_submissions, llm_collection_requests, "
				+ "member_payroll_schedules, family_members, families restart identity cascade"
		);
		CAPTURED_REQUEST.set(null);
		EXCHANGE_HANDLER.set(this::writeUnexpectedRequestResponse);
		reset(clock);
	}

	@AfterAll
	static void stopWebhookServer() {
		WEBHOOK_SERVER.stop(0);
	}

	@Test
	@DisplayName("Scheduler вызывает реальный webhook n8n, передает bearer token и переводит запрос в accepted")
	void runDailyPayrollCollectionCallsRealWebhookAndPersistsAcceptedRequest() throws Exception {
		useCurrentInstant(Instant.parse("2026-03-17T06:00:00Z"));
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1);
		EXCHANGE_HANDLER.set((exchange, request) -> writeAcceptedResponse(exchange, request, "n8n-run-accepted-001"));

		payrollCollectionOrchestrationService.runDailyPayrollCollection();

		CapturedRequest capturedRequest = CAPTURED_REQUEST.get();
		assertThat(capturedRequest).isNotNull();
		assertThat(capturedRequest.method()).isEqualTo("POST");
		assertThat(capturedRequest.path()).isEqualTo("/webhook/finance-intake-start");
		assertThat(capturedRequest.authorization()).isEqualTo("Bearer migration-n8n-webhook-token");
		assertThat(capturedRequest.contentType()).startsWith("application/json");
		assertThat(capturedRequest.accept()).contains("application/json");

		JsonNode outboundPayload = OBJECT_MAPPER.readTree(capturedRequest.body());
		String requestId = outboundPayload.path("request_id").asText();
		assertThat(UUID.fromString(requestId)).isNotNull();
		assertThat(outboundPayload.path("member").path("telegram_chat_id").asText()).isEqualTo("123456789");
		assertThat(outboundPayload.path("callback").path("submit_url").asText())
			.isEqualTo("https://server.migration.local/api/v1/intake/user-finance-data");

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					request_id,
					status,
					workflow_run_id,
					response_payload #>> '{workflow_run_id}' as payload_workflow_run_id
				from llm_collection_requests
				"""
		);

		assertThat(llmCollectionRequestRepository.findAll()).hasSize(1);
		assertThat(storedRow.get("request_id")).isEqualTo(requestId);
		assertThat(storedRow.get("status")).isEqualTo("accepted");
		assertThat(storedRow.get("workflow_run_id")).isEqualTo("n8n-run-accepted-001");
		assertThat(storedRow.get("payload_workflow_run_id")).isEqualTo("n8n-run-accepted-001");
	}

	@Test
	@DisplayName("Scheduler переводит запрос в failed, если реальный webhook n8n вернул ошибку")
	void runDailyPayrollCollectionPersistsFailedLifecycleWhenWebhookReturnsHttpError() {
		useCurrentInstant(Instant.parse("2026-03-17T06:00:00Z"));
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1);
		EXCHANGE_HANDLER.set((exchange, request) -> writeJsonResponse(
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

		payrollCollectionOrchestrationService.runDailyPayrollCollection();

		CapturedRequest capturedRequest = CAPTURED_REQUEST.get();
		assertThat(capturedRequest).isNotNull();
		assertThat(capturedRequest.authorization()).isEqualTo("Bearer migration-n8n-webhook-token");

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					status,
					workflow_run_id,
					response_payload #>> '{error,code}' as payload_error_code,
					error_message
				from llm_collection_requests
				"""
		);

		assertThat(storedRow.get("status")).isEqualTo("failed");
		assertThat(storedRow.get("workflow_run_id")).isNull();
		assertThat(storedRow.get("payload_error_code")).isEqualTo("INVALID_TOKEN");
		assertThat(storedRow.get("error_message")).isEqualTo("n8n вернул HTTP 401: Authorization shared secret is invalid");
	}

	@Test
	@DisplayName("Сквозной payroll flow переводит запрос из pending в accepted и затем в completed после intake callback")
	void payrollFlowCompletesLifecycleAfterIntakeCallback() throws Exception {
		useCurrentInstant(Instant.parse("2026-03-17T06:00:00Z"));
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1);
		EXCHANGE_HANDLER.set((exchange, request) -> writeAcceptedResponse(exchange, request, "n8n-run-e2e-001"));

		payrollCollectionOrchestrationService.runDailyPayrollCollection();

		Map<String, Object> acceptedRequestRow = jdbcTemplate.queryForMap(
			"""
				select
					id::text as id,
					request_id,
					status,
					accepted_at
				from llm_collection_requests
				"""
		);
		String requestId = acceptedRequestRow.get("request_id").toString();
		String llmCollectionRequestId = acceptedRequestRow.get("id").toString();

		assertThat(UUID.fromString(requestId)).isNotNull();
		assertThat(acceptedRequestRow.get("status")).isEqualTo("accepted");
		assertThat(acceptedRequestRow.get("accepted_at")).isNotNull();

		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayloadWithRequestId(requestId)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("accepted"))
			.andExpect(jsonPath("$.recalculation_scheduled").value(true));

		Map<String, Object> completedRequestRow = jdbcTemplate.queryForMap(
			"""
				select
					status,
					accepted_at,
					completed_at
				from llm_collection_requests
				where id = ?
				""",
			UUID.fromString(llmCollectionRequestId)
		);
		Integer memberSnapshotsCount = jdbcTemplate.queryForObject(
			"select count(*) from member_finance_snapshots where member_id = ? and period_year = ? and period_month = ?",
			Integer.class,
			MEMBER_ID,
			2026,
			(short) 3
		);
		Integer familySnapshotsCount = jdbcTemplate.queryForObject(
			"select count(*) from family_dashboard_snapshots where family_id = ? and period_year = ? and period_month = ?",
			Integer.class,
			FAMILY_ID,
			2026,
			(short) 3
		);
		Map<String, Object> storedSubmissionRow = jdbcTemplate.queryForMap(
			"""
				select
					request_id,
					llm_collection_request_id::text as llm_collection_request_id
				from finance_submissions
				"""
		);

		assertThat(completedRequestRow.get("status")).isEqualTo("completed");
		assertThat(completedRequestRow.get("accepted_at")).isNotNull();
		assertThat(completedRequestRow.get("completed_at")).isNotNull();
		assertThat(memberSnapshotsCount).isEqualTo(1);
		assertThat(familySnapshotsCount).isEqualTo(1);
		assertThat(storedSubmissionRow.get("request_id")).isEqualTo(requestId);
		assertThat(storedSubmissionRow.get("llm_collection_request_id")).isEqualTo(llmCollectionRequestId);
	}

	private void useCurrentInstant(Instant instant) {
		given(clock.getZone()).willReturn(APPLICATION_ZONE);
		given(clock.instant()).willReturn(instant);
		given(clock.withZone(any(ZoneId.class)))
			.willAnswer(invocation -> Clock.fixed(instant, invocation.getArgument(0, ZoneId.class)));
	}

	private void insertPayrollSchedule(
		String familyTimezone,
		Short dayOfMonth,
		Short triggerDelayDays
	) {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T09:00:00+03:00");
		jdbcTemplate.update(
			"""
				insert into families (id, name, timezone, currency_code, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
			FAMILY_ID,
			"Ivanov family",
			familyTimezone,
			"RUB",
			"active",
			now,
			now
		);
		jdbcTemplate.update(
			"""
				insert into family_members (
					id,
					family_id,
					first_name,
					last_name,
					display_name,
					telegram_chat_id,
					telegram_username,
					is_active,
					created_at,
					updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
			MEMBER_ID,
			FAMILY_ID,
			"Anna",
			"Ivanova",
			"Anna",
			"123456789",
			"anna_ivanova",
			true,
			now.plusMinutes(5),
			now.plusMinutes(5)
		);
		jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (
					id,
					member_id,
					label,
					schedule_type,
					day_of_month,
					trigger_delay_days,
					is_active,
					created_at,
					updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
			PAYROLL_SCHEDULE_ID,
			MEMBER_ID,
			"Main salary",
			"fixed_day_of_month",
			dayOfMonth,
			triggerDelayDays,
			true,
			now.plusMinutes(10),
			now.plusMinutes(10)
		);
	}

	private String validPayloadWithRequestId(String requestId) {
		return """
			{
			  "external_submission_id": "n8n-run-2026-03-15-001",
			  "request_id": "%s",
			  "family_id": "%s",
			  "member_id": "%s",
			  "source": "telegram",
			  "collected_at": "2026-03-15T08:40:00+03:00",
			  "period": {
			    "year": 2026,
			    "month": 3
			  },
			  "finance_input": {
			    "monthly_income": 120000,
			    "monthly_expenses": 50000,
			    "monthly_credit_payments": 18000,
			    "liquid_savings": 150000
			  },
			  "meta": {
			    "telegram_chat_id": "123456789",
			    "confidence": "medium",
			    "notes": "User provided approximate values"
			  }
			}
			""".formatted(requestId, FAMILY_ID, MEMBER_ID);
	}

	private static HttpServer startWebhookServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
			server.createContext("/webhook/finance-intake-start", exchange -> {
				try {
					byte[] requestBody = exchange.getRequestBody().readAllBytes();
					CapturedRequest capturedRequest = new CapturedRequest(
						exchange.getRequestMethod(),
						exchange.getRequestURI().getPath(),
						exchange.getRequestHeaders().getFirst("Authorization"),
						exchange.getRequestHeaders().getFirst("Content-Type"),
						exchange.getRequestHeaders().getFirst("Accept"),
						new String(requestBody, UTF_8)
					);
					CAPTURED_REQUEST.set(capturedRequest);
					ExchangeHandler handler = EXCHANGE_HANDLER.get();
					if (handler == null) {
						throw new IllegalStateException("Exchange handler must not be null");
					}
					handler.handle(exchange, capturedRequest);
				} finally {
					exchange.close();
				}
			});
			server.start();
			return server;
		} catch (IOException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private void writeAcceptedResponse(
		HttpExchange exchange,
		CapturedRequest request,
		String workflowRunId
	) throws IOException {
		try {
			String requestId = OBJECT_MAPPER.readTree(request.body()).path("request_id").asText();
			writeJsonResponse(
				exchange,
				202,
				"""
					{
					  "status": "accepted",
					  "request_id": "%s",
					  "workflow_run_id": "%s"
					}
					""".formatted(requestId, workflowRunId)
			);
		} catch (IOException exception) {
			writeJsonResponse(
				exchange,
				500,
				"""
					{
					  "error": {
					    "code": "INVALID_REQUEST",
					    "message": "Unable to parse request payload"
					  }
					}
					"""
			);
			throw exception;
		}
	}

	private void writeUnexpectedRequestResponse(HttpExchange exchange, CapturedRequest request) throws IOException {
		writeJsonResponse(
			exchange,
			500,
			"""
				{
				  "error": {
				    "code": "UNEXPECTED_REQUEST",
				    "message": "Test webhook handler was not configured"
				  }
				}
				"""
		);
	}

	private void writeJsonResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
		byte[] responseBytes = responseBody.getBytes(UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, responseBytes.length);
		try (OutputStream responseStream = exchange.getResponseBody()) {
			responseStream.write(responseBytes);
		}
	}

	@FunctionalInterface
	private interface ExchangeHandler {

		void handle(HttpExchange exchange, CapturedRequest request) throws IOException;
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
