package com.deknd.familyfinancemetre;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("migration")
class LocalReferenceDataSeedSmokeTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID FIRST_MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SECOND_MEMBER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID DEVICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_seed_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void appliesManualLocalSeedAndCreatesExpectedReferenceData() {
		runSeedScript();

		assertThat(jdbcTemplate.queryForObject("select count(*) from families where id = ?", Integer.class, FAMILY_ID))
			.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from family_members where family_id = ?", Integer.class, FAMILY_ID))
			.isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("select count(*) from devices where id = ?", Integer.class, DEVICE_ID))
			.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_payroll_schedules", Integer.class))
			.isEqualTo(4);

		List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
			"""
				select member_id, schedule_type, day_of_month
				from member_payroll_schedules
				order by member_id, schedule_type, day_of_month nulls last
			"""
		);

		assertThat(schedules)
			.extracting(
				row -> row.get("member_id"),
				row -> row.get("schedule_type"),
				row -> {
					Number dayOfMonth = (Number) row.get("day_of_month");
					return dayOfMonth == null ? null : dayOfMonth.intValue();
				}
			)
			.containsExactly(
				tuple(FIRST_MEMBER_ID, "fixed_day_of_month", 16),
				tuple(FIRST_MEMBER_ID, "last_day_of_month", null),
				tuple(SECOND_MEMBER_ID, "fixed_day_of_month", 5),
				tuple(SECOND_MEMBER_ID, "fixed_day_of_month", 25)
			);

		Map<String, Object> deviceRow = jdbcTemplate.queryForMap(
			"""
				select family_id, name, device_token_hash, status
				from devices
				where id = ?
			""",
			DEVICE_ID
		);

		assertThat(deviceRow.get("family_id")).isEqualTo(FAMILY_ID);
		assertThat(deviceRow.get("name")).isEqualTo("Hall display");
		assertThat(deviceRow.get("device_token_hash"))
			.isEqualTo("e9bdbf88b2ec36a3e0bd9d60f2cd413a1631d5dd2f6053d342dc27d24fa4a447");
		assertThat(deviceRow.get("status")).isEqualTo("active");
	}

	@Test
	void manualLocalSeedIsIdempotent() {
		runSeedScript();
		runSeedScript();

		assertThat(jdbcTemplate.queryForObject("select count(*) from families", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from family_members", Integer.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("select count(*) from devices", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_payroll_schedules", Integer.class)).isEqualTo(4);
	}

	private void runSeedScript() {
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
			new ClassPathResource("db/manual/local_seed_reference_data.sql")
		);
		populator.execute(dataSource);
	}
}
