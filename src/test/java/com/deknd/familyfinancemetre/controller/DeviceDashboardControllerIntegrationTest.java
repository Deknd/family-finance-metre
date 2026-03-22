package com.deknd.familyfinancemetre.controller;

import com.deknd.familyfinancemetre.security.DeviceTokenHasher;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("migration")
class DeviceDashboardControllerIntegrationTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID DEVICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID SNAPSHOT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
	private static final String DEVICE_TOKEN = "local-device-token-family-001";

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_device_dashboard_test")
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
		jdbcTemplate.execute("truncate table family_dashboard_snapshots, devices, families restart identity cascade");
		insertFamily();
		insertDevice();
	}

	@Test
	@DisplayName("GET /api/v1/device/dashboard возвращает готовый payload для аутентифицированного устройства")
	void getDashboardReturnsReadyPayload() throws Exception {
		insertDashboardSnapshot();
		Instant requestedAt = Instant.now();

		mockMvc.perform(get("/api/v1/device/dashboard").header("X-Device-Token", DEVICE_TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generated_at").value("2026-03-15T09:00:00+03:00"))
			.andExpect(jsonPath("$.device_id").value(DEVICE_ID.toString()))
			.andExpect(jsonPath("$.family_id").value(FAMILY_ID.toString()))
			.andExpect(jsonPath("$.status").value("warning"))
			.andExpect(jsonPath("$.status_text").value("Внимание"))
			.andExpect(jsonPath("$.status_reason").value("Подушка ниже комфортной зоны"))
			.andExpect(jsonPath("$.metrics.monthly_income").value(210000))
			.andExpect(jsonPath("$.metrics.monthly_expenses").value(90000))
			.andExpect(jsonPath("$.metrics.credit_load_percent").value(new BigDecimal("27.00").doubleValue()))
			.andExpect(jsonPath("$.metrics.emergency_fund_months").value(new BigDecimal("2.00").doubleValue()))
			.andExpect(jsonPath("$.display.currency").value("RUB"))
			.andExpect(jsonPath("$.display.updated_at_label").value("15.03 09:00"));
		Instant handledAt = Instant.now();
		OffsetDateTime actualLastSeenAt = getDeviceLastSeenAt();

		assertThat(actualLastSeenAt).isNotNull();
		assertThat(actualLastSeenAt.toInstant()).isBetween(requestedAt, handledAt);
	}

	@Test
	@DisplayName("GET /api/v1/device/dashboard возвращает 404, если готовый dashboard snapshot еще не рассчитан")
	void getDashboardReturnsNotFoundWhenSnapshotIsMissing() throws Exception {
		mockMvc.perform(get("/api/v1/device/dashboard").header("X-Device-Token", DEVICE_TOKEN))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("DASHBOARD_NOT_READY"))
			.andExpect(jsonPath("$.error.message").value("Dashboard data is not available yet"));

		assertThat(getDeviceLastSeenAt()).isNull();
	}

	@Test
	@DisplayName("GET /api/v1/device/dashboard возвращает 401, если передан неверный токен устройства")
	void getDashboardReturnsUnauthorizedWhenDeviceTokenIsInvalid() throws Exception {
		mockMvc.perform(get("/api/v1/device/dashboard").header("X-Device-Token", "wrong-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_DEVICE_TOKEN"))
			.andExpect(jsonPath("$.error.message").value("Device token is invalid"));

		assertThat(getDeviceLastSeenAt()).isNull();
	}

	private void insertFamily() {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T08:00:00+03:00");
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

	private void insertDevice() {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T08:10:00+03:00");
		jdbcTemplate.update(
			"""
				insert into devices (id, family_id, name, device_token_hash, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
			DEVICE_ID,
			FAMILY_ID,
			"Hall display",
			deviceTokenHasher.hash(DEVICE_TOKEN),
			"active",
			now,
			now
		);
	}

	private void insertDashboardSnapshot() {
		OffsetDateTime now = OffsetDateTime.parse("2026-03-15T09:00:00+03:00");
		jdbcTemplate.update(
			"""
				insert into family_dashboard_snapshots (
					id,
					family_id,
					period_year,
					period_month,
					status,
					status_text,
					status_reason,
					monthly_income,
					monthly_expenses,
					credit_load_percent,
					emergency_fund_months,
					member_count_used,
					calculated_at,
					created_at,
					updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
			SNAPSHOT_ID,
			FAMILY_ID,
			2026,
			(short) 3,
			"warning",
			"Внимание",
			"Подушка ниже комфортной зоны",
			210000,
			90000,
			new BigDecimal("27.00"),
			new BigDecimal("2.00"),
			2,
			now,
			now,
			now
		);
	}

	private OffsetDateTime getDeviceLastSeenAt() {
		return jdbcTemplate.queryForObject(
			"select last_seen_at from devices where id = ?",
			OffsetDateTime.class,
			DEVICE_ID
		);
	}
}
