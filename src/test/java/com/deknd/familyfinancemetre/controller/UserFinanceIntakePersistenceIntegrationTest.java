package com.deknd.familyfinancemetre.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("migration")
class UserFinanceIntakePersistenceIntegrationTest {

	private static final String API_KEY = "migration-n8n-api-key";
	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID ANOTHER_FAMILY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID ANOTHER_MEMBER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_intake_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table member_finance_snapshots, finance_submissions, family_members, families restart identity cascade");
		insertFamily();
		insertMember();
	}

	@Test
	void validPayloadPersistsSubmissionCreatesMemberSnapshotAndReturnsStoredId() throws Exception {
		String responseBody = mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayload()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("accepted"))
			.andExpect(jsonPath("$.family_id").value(FAMILY_ID.toString()))
			.andExpect(jsonPath("$.member_id").value(MEMBER_ID.toString()))
			.andExpect(jsonPath("$.recalculation_scheduled").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode responseJson = objectMapper.readTree(responseBody);
		UUID submissionId = UUID.fromString(responseJson.get("submission_id").asText());

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					external_submission_id,
					family_id::text as family_id,
					member_id::text as member_id,
					monthly_income,
					monthly_expenses,
					monthly_credit_payments,
					liquid_savings,
					raw_payload ->> 'external_submission_id' as raw_external_submission_id
				from finance_submissions
				where id = ?
				""",
			submissionId
		);

		assertThat(storedRow.get("external_submission_id")).isEqualTo("n8n-run-2026-03-15-001");
		assertThat(storedRow.get("family_id")).isEqualTo(FAMILY_ID.toString());
		assertThat(storedRow.get("member_id")).isEqualTo(MEMBER_ID.toString());
		assertThat(storedRow.get("monthly_income")).isEqualTo(120000);
		assertThat(storedRow.get("monthly_expenses")).isEqualTo(50000);
		assertThat(storedRow.get("monthly_credit_payments")).isEqualTo(18000);
		assertThat(storedRow.get("liquid_savings")).isEqualTo(150000);
		assertThat(storedRow.get("raw_external_submission_id")).isEqualTo("n8n-run-2026-03-15-001");

		Map<String, Object> storedSnapshotRow = jdbcTemplate.queryForMap(
			"""
				select
					family_id::text as family_id,
					member_id::text as member_id,
					period_year,
					period_month,
					source_submission_id::text as source_submission_id,
					monthly_income,
					monthly_expenses,
					monthly_credit_payments,
					liquid_savings,
					collected_at
				from member_finance_snapshots
				where member_id = ? and period_year = ? and period_month = ?
				""",
			MEMBER_ID,
			2026,
			(short) 3
		);

		assertThat(storedSnapshotRow.get("family_id")).isEqualTo(FAMILY_ID.toString());
		assertThat(storedSnapshotRow.get("member_id")).isEqualTo(MEMBER_ID.toString());
		assertThat(storedSnapshotRow.get("period_year")).isEqualTo(2026);
		assertThat(((Number) storedSnapshotRow.get("period_month")).shortValue()).isEqualTo((short) 3);
		assertThat(storedSnapshotRow.get("source_submission_id")).isEqualTo(submissionId.toString());
		assertThat(storedSnapshotRow.get("monthly_income")).isEqualTo(120000);
		assertThat(storedSnapshotRow.get("monthly_expenses")).isEqualTo(50000);
		assertThat(storedSnapshotRow.get("monthly_credit_payments")).isEqualTo(18000);
		assertThat(storedSnapshotRow.get("liquid_savings")).isEqualTo(150000);
		assertThat(storedSnapshotRow.get("collected_at").toString()).contains("2026-03-15 08:40:00");
	}

	@Test
	void duplicateExternalSubmissionIdReturnsConflictAndKeepsSingleRow() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayload()))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayload()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DUPLICATE_SUBMISSION"))
			.andExpect(jsonPath("$.error.message").value("Submission with this external_submission_id already exists"));

		Integer submissionsCount = jdbcTemplate.queryForObject(
			"select count(*) from finance_submissions where external_submission_id = ?",
			Integer.class,
			"n8n-run-2026-03-15-001"
		);

		assertThat(submissionsCount).isEqualTo(1);
	}

	@Test
	void payloadWithRequestIdPersistsCorrelationId() throws Exception {
		String responseBody = mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayloadWithRequestId()))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode responseJson = objectMapper.readTree(responseBody);
		UUID submissionId = UUID.fromString(responseJson.get("submission_id").asText());

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					request_id,
					raw_payload ->> 'request_id' as raw_request_id
				from finance_submissions
				where id = ?
				""",
			submissionId
		);

		assertThat(storedRow.get("request_id")).isEqualTo("req-2026-03-15-member-anna");
		assertThat(storedRow.get("raw_request_id")).isEqualTo("req-2026-03-15-member-anna");
	}

	@Test
	void secondPayloadForSamePeriodUpdatesExistingMemberSnapshotAndAggregatesMonthlyIncome() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayload()))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(secondPayloadForSamePeriodWithNewerCollectedAt()))
			.andExpect(status().isAccepted());

		Integer snapshotCount = jdbcTemplate.queryForObject(
			"select count(*) from member_finance_snapshots where member_id = ? and period_year = ? and period_month = ?",
			Integer.class,
			MEMBER_ID,
			2026,
			(short) 3
		);

		Map<String, Object> storedSnapshotRow = jdbcTemplate.queryForMap(
			"""
				select
					source_submission_id::text as source_submission_id,
					monthly_income,
					monthly_expenses,
					monthly_credit_payments,
					liquid_savings,
					collected_at
				from member_finance_snapshots
				where member_id = ? and period_year = ? and period_month = ?
				""",
			MEMBER_ID,
			2026,
			(short) 3
		);

		assertThat(snapshotCount).isEqualTo(1);
		assertThat(storedSnapshotRow.get("monthly_income")).isEqualTo(190000);
		assertThat(storedSnapshotRow.get("monthly_expenses")).isEqualTo(62000);
		assertThat(storedSnapshotRow.get("monthly_credit_payments")).isEqualTo(15000);
		assertThat(storedSnapshotRow.get("liquid_savings")).isEqualTo(210000);
		assertThat(storedSnapshotRow.get("collected_at").toString()).contains("2026-03-20 09:15:00");

		String latestSubmissionId = jdbcTemplate.queryForObject(
			"select id::text from finance_submissions where external_submission_id = ?",
			String.class,
			"n8n-run-2026-03-20-002"
		);
		assertThat(storedSnapshotRow.get("source_submission_id")).isEqualTo(latestSubmissionId);
	}

	@Test
	void olderPayloadForSamePeriodDoesNotOverwriteSnapshotFieldsFromFreshestSubmission() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayload()))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(secondPayloadForSamePeriodWithOlderCollectedAt()))
			.andExpect(status().isAccepted());

		Map<String, Object> storedSnapshotRow = jdbcTemplate.queryForMap(
			"""
				select
					source_submission_id::text as source_submission_id,
					monthly_income,
					monthly_expenses,
					monthly_credit_payments,
					liquid_savings,
					collected_at
				from member_finance_snapshots
				where member_id = ? and period_year = ? and period_month = ?
				""",
			MEMBER_ID,
			2026,
			(short) 3
		);

		String freshestSubmissionId = jdbcTemplate.queryForObject(
			"select id::text from finance_submissions where external_submission_id = ?",
			String.class,
			"n8n-run-2026-03-15-001"
		);

		assertThat(storedSnapshotRow.get("monthly_income")).isEqualTo(165000);
		assertThat(storedSnapshotRow.get("monthly_expenses")).isEqualTo(50000);
		assertThat(storedSnapshotRow.get("monthly_credit_payments")).isEqualTo(18000);
		assertThat(storedSnapshotRow.get("liquid_savings")).isEqualTo(150000);
		assertThat(storedSnapshotRow.get("source_submission_id")).isEqualTo(freshestSubmissionId);
		assertThat(storedSnapshotRow.get("collected_at").toString()).contains("2026-03-15 08:40:00");
	}

	@Test
	void missingFamilyReferenceReturnsValidationErrorAndDoesNotPersistSubmission() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayload().replace(
					"\"family_id\": \"11111111-1111-1111-1111-111111111111\"",
					"\"family_id\": \"44444444-4444-4444-4444-444444444444\""
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[0].field").value("family_id"))
			.andExpect(jsonPath("$.details[0].message").value("family does not exist"));

		Integer submissionsCount = jdbcTemplate.queryForObject("select count(*) from finance_submissions", Integer.class);
		assertThat(submissionsCount).isZero();
	}

	@Test
	void memberFromAnotherFamilyReturnsValidationErrorAndDoesNotPersistSubmission() throws Exception {
		insertFamily(ANOTHER_FAMILY_ID, "Another family");
		insertMember(ANOTHER_MEMBER_ID, ANOTHER_FAMILY_ID, "Olga", "Petrova", "olga_petrova");

		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(APPLICATION_JSON)
				.content(validPayload().replace(
					"\"member_id\": \"22222222-2222-2222-2222-222222222222\"",
					"\"member_id\": \"55555555-5555-5555-5555-555555555555\""
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[0].field").value("member_id"))
			.andExpect(jsonPath("$.details[0].message").value("member does not belong to the specified family"));

		Integer submissionsCount = jdbcTemplate.queryForObject("select count(*) from finance_submissions", Integer.class);
		assertThat(submissionsCount).isZero();
	}

	private void insertFamily() {
		insertFamily(FAMILY_ID, "Demo family");
	}

	private void insertFamily(UUID familyId, String familyName) {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T09:00:00+03:00");
		jdbcTemplate.update(
			"""
				insert into families (id, name, timezone, currency_code, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
			familyId,
			familyName,
			"Europe/Moscow",
			"RUB",
			"active",
			now,
			now
		);
	}

	private void insertMember() {
		insertMember(MEMBER_ID, FAMILY_ID, "Anna", "Ivanova", "anna_ivanova");
	}

	private void insertMember(
		UUID memberId,
		UUID familyId,
		String firstName,
		String lastName,
		String telegramUsername
	) {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T09:05:00+03:00");
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
			memberId,
			familyId,
			firstName,
			lastName,
			firstName,
			"chat-" + memberId,
			telegramUsername,
			true,
			now,
			now
		);
	}

	private String validPayload() {
		return """
			{
			  "external_submission_id": "n8n-run-2026-03-15-001",
			  "family_id": "11111111-1111-1111-1111-111111111111",
			  "member_id": "22222222-2222-2222-2222-222222222222",
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
			""";
	}

	private String validPayloadWithRequestId() {
		return """
			{
			  "external_submission_id": "n8n-run-2026-03-15-001",
			  "request_id": "req-2026-03-15-member-anna",
			  "family_id": "11111111-1111-1111-1111-111111111111",
			  "member_id": "22222222-2222-2222-2222-222222222222",
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
			""";
	}

	private String secondPayloadForSamePeriodWithNewerCollectedAt() {
		return """
			{
			  "external_submission_id": "n8n-run-2026-03-20-002",
			  "family_id": "11111111-1111-1111-1111-111111111111",
			  "member_id": "22222222-2222-2222-2222-222222222222",
			  "source": "telegram",
			  "collected_at": "2026-03-20T09:15:00+03:00",
			  "period": {
			    "year": 2026,
			    "month": 3
			  },
			  "finance_input": {
			    "monthly_income": 70000,
			    "monthly_expenses": 62000,
			    "monthly_credit_payments": 15000,
			    "liquid_savings": 210000
			  },
			  "meta": {
			    "telegram_chat_id": "123456789",
			    "confidence": "high",
			    "notes": "Updated estimate after second payroll"
			  }
			}
			""";
	}

	private String secondPayloadForSamePeriodWithOlderCollectedAt() {
		return """
			{
			  "external_submission_id": "n8n-run-2026-03-10-002",
			  "family_id": "11111111-1111-1111-1111-111111111111",
			  "member_id": "22222222-2222-2222-2222-222222222222",
			  "source": "telegram",
			  "collected_at": "2026-03-10T08:30:00+03:00",
			  "period": {
			    "year": 2026,
			    "month": 3
			  },
			  "finance_input": {
			    "monthly_income": 45000,
			    "monthly_expenses": 47000,
			    "monthly_credit_payments": 17000,
			    "liquid_savings": 120000
			  },
			  "meta": {
			    "telegram_chat_id": "123456789",
			    "confidence": "medium",
			    "notes": "Late delivery for earlier payroll event"
			  }
			}
			""";
	}
}
