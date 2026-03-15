package com.deknd.familyfinancemetre.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class UserFinanceIntakeControllerIntegrationTest {

	private static final String API_KEY = "test-n8n-api-key";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void validPayloadReturnsAccepted() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("accepted"))
			.andExpect(jsonPath("$.submission_id").isString())
			.andExpect(jsonPath("$.family_id").value("11111111-1111-1111-1111-111111111111"))
			.andExpect(jsonPath("$.member_id").value("22222222-2222-2222-2222-222222222222"))
			.andExpect(jsonPath("$.recalculation_scheduled").value(true));
	}

	@Test
	void missingApiKeyReturnsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"))
			.andExpect(jsonPath("$.error.message").value("API key is invalid"));
	}

	@Test
	void missingRequiredFieldReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload().replace("  \"external_submission_id\": \"n8n-run-2026-03-15-001\",\n", "")))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[*].field").value(hasItem("external_submission_id")))
			.andExpect(jsonPath("$.details[*].message").value(hasItem("must not be blank")));
	}

	@Test
	void negativeMoneyValueReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload().replace("\"monthly_income\": 120000", "\"monthly_income\": -1")))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.details[*].field").value(hasItem("finance_input.monthly_income")))
			.andExpect(jsonPath("$.details[*].message").value(hasItem("must be greater than or equal to 0")));
	}

	@Test
	void invalidPeriodMonthReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload().replace("\"month\": 3", "\"month\": 13")))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.details[*].field").value(hasItem("period.month")));
	}

	@Test
	void invalidUuidReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload().replace(
					"\"family_id\": \"11111111-1111-1111-1111-111111111111\"",
					"\"family_id\": \"family_01\""
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.details[0].field").value("family_id"))
			.andExpect(jsonPath("$.details[0].message").value("must be a valid UUID"));
	}

	@Test
	void invalidSourceReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload().replace("\"source\": \"telegram\"", "\"source\": \"email\"")))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.details[0].field").value("source"))
			.andExpect(jsonPath("$.details[0].message").value("must be one of: telegram"));
	}

	@Test
	void invalidConfidenceReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload().replace("\"confidence\": \"medium\"", "\"confidence\": \"certain\"")))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.details[0].field").value("meta.confidence"))
			.andExpect(jsonPath("$.details[0].message").value("must be one of: low, medium, high"));
	}

	@Test
	void invalidCollectedAtReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPayload().replace(
					"\"collected_at\": \"2026-03-15T08:40:00+03:00\"",
					"\"collected_at\": \"2026/03/15 08:40\""
				)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.details[0].field").value("collected_at"))
			.andExpect(jsonPath("$.details[0].message").value("must be a valid ISO-8601 date-time with offset"));
	}

	@Test
	void malformedJsonReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/v1/intake/user-finance-data")
				.header("X-API-Key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"external_submission_id\":"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.details[0].field").value("body"))
			.andExpect(jsonPath("$.details[0].message").value("Malformed JSON request"));
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
