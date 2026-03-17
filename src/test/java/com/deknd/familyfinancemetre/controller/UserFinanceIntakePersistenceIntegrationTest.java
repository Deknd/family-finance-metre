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
		jdbcTemplate.execute("truncate table finance_submissions, family_members, families restart identity cascade");
		insertFamily();
		insertMember();
	}

	@Test
	void validPayloadPersistsSubmissionAndReturnsStoredId() throws Exception {
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

	private void insertFamily() {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T09:00:00+03:00");
		jdbcTemplate.update(
			"""
				insert into families (id, name, timezone, currency_code, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
			FAMILY_ID,
			"Demo family",
			"Europe/Moscow",
			"RUB",
			"active",
			now,
			now
		);
	}

	private void insertMember() {
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
			MEMBER_ID,
			FAMILY_ID,
			"Anna",
			"Ivanova",
			"Anna",
			"123456789",
			"anna_ivanova",
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
}
