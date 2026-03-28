package com.deknd.familyfinancemetre.shared.security;

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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("migration")
class SecurityIntegrationTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID ACTIVE_DEVICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID DISABLED_DEVICE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_security_test")
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

	@Autowired
	private DeviceTokenHasher deviceTokenHasher;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table devices, families restart identity cascade");
		insertFamily();
		insertDevice(ACTIVE_DEVICE_ID, "Hall display", "local-device-token-family-001", "active");
		insertDevice(DISABLED_DEVICE_ID, "Kitchen display", "disabled-device-token", "disabled");
	}

	@Test
	void actuatorHealthIsAvailableWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void deviceEndpointWithoutHeaderReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/device/test-auth"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_DEVICE_TOKEN"))
			.andExpect(jsonPath("$.error.message").value("Device token is invalid"));
	}

	@Test
	void deviceEndpointWithInvalidHeaderReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/device/test-auth").header("X-Device-Token", "wrong-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_DEVICE_TOKEN"))
			.andExpect(jsonPath("$.error.message").value("Device token is invalid"));
	}

	@Test
	void deviceEndpointWithActiveDeviceTokenAuthenticatesAndExposesPrincipal() throws Exception {
		mockMvc.perform(get("/api/v1/device/test-auth").header("X-Device-Token", "local-device-token-family-001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deviceId").value(ACTIVE_DEVICE_ID.toString()))
			.andExpect(jsonPath("$.familyId").value(FAMILY_ID.toString()))
			.andExpect(jsonPath("$.deviceName").value("Hall display"));
	}

	@Test
	void disabledDeviceTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/device/test-auth").header("X-Device-Token", "disabled-device-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_DEVICE_TOKEN"))
			.andExpect(jsonPath("$.error.message").value("Device token is invalid"));
	}

	@Test
	void intakeEndpointWithoutHeaderReturnsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/intake/test-auth"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"))
			.andExpect(jsonPath("$.error.message").value("API key is invalid"));
	}

	@Test
	void intakeEndpointWithInvalidApiKeyReturnsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/intake/test-auth").header("X-API-Key", "wrong-api-key"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"))
			.andExpect(jsonPath("$.error.message").value("API key is invalid"));
	}

	@Test
	void intakeEndpointWithValidApiKeyAuthenticatesAndExposesPrincipal() throws Exception {
		mockMvc.perform(post("/api/v1/intake/test-auth").header("X-API-Key", "migration-n8n-api-key"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.integrationName").value("n8n"));
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

	private void insertDevice(UUID id, String name, String rawToken, String status) {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T09:10:00+03:00");
		jdbcTemplate.update(
			"""
				insert into devices (id, family_id, name, device_token_hash, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
			id,
			FAMILY_ID,
			name,
			deviceTokenHasher.hash(rawToken),
			status,
			now,
			now
		);
	}
}

