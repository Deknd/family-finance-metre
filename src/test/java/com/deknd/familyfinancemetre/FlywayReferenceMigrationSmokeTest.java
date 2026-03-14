package com.deknd.familyfinancemetre;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("migration")
class FlywayReferenceMigrationSmokeTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_migration_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void appliesReferenceMigrationAndRecordsHistory() {
		assertThat(tableExists("families")).isTrue();
		assertThat(tableExists("family_members")).isTrue();
		assertThat(tableExists("devices")).isTrue();
		assertThat(tableExists("member_payroll_schedules")).isTrue();

		Boolean referenceMigrationApplied = jdbcTemplate.queryForObject(
			"""
				select success
				from flyway_schema_history
				where version = '1'
			""",
			Boolean.class
		);
		Boolean payrollMigrationApplied = jdbcTemplate.queryForObject(
			"""
				select success
				from flyway_schema_history
				where version = '2'
			""",
			Boolean.class
		);

		assertThat(referenceMigrationApplied).isTrue();
		assertThat(payrollMigrationApplied).isTrue();
	}

	@Test
	void familiesTableProvidesDocumentedDefaults() {
		UUID familyId = UUID.randomUUID();

		jdbcTemplate.update("insert into families (id, name) values (?, ?)", familyId, "Ivanov family");

		Map<String, Object> familyRow = jdbcTemplate.queryForMap(
			"""
				select timezone, currency_code, status, created_at, updated_at
				from families
				where id = ?
			""",
			familyId
		);

		assertThat(familyRow.get("timezone")).isEqualTo("Europe/Moscow");
		assertThat(familyRow.get("currency_code")).isEqualTo("RUB");
		assertThat(familyRow.get("status")).isEqualTo("active");
		assertThat(familyRow.get("created_at")).isNotNull();
		assertThat(familyRow.get("updated_at")).isNotNull();
	}

	@Test
	void familyMembersSupportNullableFieldsDefaultFlagForeignKeyAndPartialUniqueTelegramIndex() {
		UUID familyId = insertFamily("Petrov family");
		UUID memberId = UUID.randomUUID();
		UUID secondMemberId = UUID.randomUUID();

		jdbcTemplate.update(
			"""
				insert into family_members (id, family_id, first_name)
				values (?, ?, ?)
			""",
			memberId,
			familyId,
			"Anna"
		);

		Map<String, Object> memberRow = jdbcTemplate.queryForMap(
			"""
				select last_name, display_name, telegram_chat_id, telegram_username, is_active, created_at, updated_at
				from family_members
				where id = ?
			""",
			memberId
		);

		assertThat(memberRow.get("last_name")).isNull();
		assertThat(memberRow.get("display_name")).isNull();
		assertThat(memberRow.get("telegram_chat_id")).isNull();
		assertThat(memberRow.get("telegram_username")).isNull();
		assertThat(memberRow.get("is_active")).isEqualTo(true);
		assertThat(memberRow.get("created_at")).isNotNull();
		assertThat(memberRow.get("updated_at")).isNotNull();

		jdbcTemplate.update(
			"""
				insert into family_members (id, family_id, first_name)
				values (?, ?, ?)
			""",
			secondMemberId,
			familyId,
			"Ivan"
		);

		String sharedTelegramChatId = "123456789-" + UUID.randomUUID();
		jdbcTemplate.update(
			"""
				insert into family_members (id, family_id, first_name, telegram_chat_id)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			"Maria",
			sharedTelegramChatId
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into family_members (id, family_id, first_name, telegram_chat_id)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			"Oleg",
			sharedTelegramChatId
		)).isInstanceOf(DataIntegrityViolationException.class);

		String telegramChatIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'uq_family_members_telegram_chat_id_not_null'
			""",
			String.class
		);

		assertThat(telegramChatIndexDefinition).isNotNull();
		assertThat(telegramChatIndexDefinition.toLowerCase())
			.contains("create unique index uq_family_members_telegram_chat_id_not_null")
			.contains("on public.family_members")
			.contains("where (telegram_chat_id is not null)");

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into family_members (id, family_id, first_name)
				values (?, ?, ?)
			""",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"Ghost"
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void devicesSupportNullableLastSeenDefaultStatusForeignKeyAndUniqueTokenHash() {
		UUID familyId = insertFamily("Sidorov family");
		UUID deviceId = UUID.randomUUID();
		String deviceTokenHash = "token-hash-" + UUID.randomUUID();

		jdbcTemplate.update(
			"""
				insert into devices (id, family_id, name, device_token_hash)
				values (?, ?, ?, ?)
			""",
			deviceId,
			familyId,
			"Hall display",
			deviceTokenHash
		);

		Map<String, Object> deviceRow = jdbcTemplate.queryForMap(
			"""
				select status, last_seen_at, created_at, updated_at
				from devices
				where id = ?
			""",
			deviceId
		);

		assertThat(deviceRow.get("status")).isEqualTo("active");
		assertThat(deviceRow.get("last_seen_at")).isNull();
		assertThat(deviceRow.get("created_at")).isNotNull();
		assertThat(deviceRow.get("updated_at")).isNotNull();

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into devices (id, family_id, name, device_token_hash)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			"Kitchen display",
			deviceTokenHash
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into devices (id, family_id, name, device_token_hash)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"Orphan display",
			"token-hash-" + UUID.randomUUID()
		)).isInstanceOf(DataIntegrityViolationException.class);

		String deviceTokenIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'uq_devices_device_token_hash'
			""",
			String.class
		);

		assertThat(deviceTokenIndexDefinition).isNotNull();
		assertThat(deviceTokenIndexDefinition.toLowerCase())
			.contains("create unique index uq_devices_device_token_hash")
			.contains("on public.devices")
			.contains("(device_token_hash)");
	}

	@Test
	void memberPayrollSchedulesSupportMultipleEventsDefaultsChecksForeignKeyAndUniqueExpressionIndex() {
		UUID familyId = insertFamily("Smirnov family");
		UUID memberId = insertFamilyMember(familyId, "Alex");
		UUID fixedDayScheduleId = UUID.randomUUID();
		UUID lastDayScheduleId = UUID.randomUUID();
		UUID secondFixedDayScheduleId = UUID.randomUUID();

		jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, label, schedule_type, day_of_month)
				values (?, ?, ?, ?, ?)
			""",
			fixedDayScheduleId,
			memberId,
			"Main salary",
			"fixed_day_of_month",
			16
		);

		Map<String, Object> fixedDayScheduleRow = jdbcTemplate.queryForMap(
			"""
				select label, day_of_month, trigger_delay_days, is_active, created_at, updated_at
				from member_payroll_schedules
				where id = ?
			""",
			fixedDayScheduleId
		);

		assertThat(fixedDayScheduleRow.get("label")).isEqualTo("Main salary");
		assertThat(((Number) fixedDayScheduleRow.get("day_of_month")).intValue()).isEqualTo(16);
		assertThat(((Number) fixedDayScheduleRow.get("trigger_delay_days")).intValue()).isEqualTo(1);
		assertThat(fixedDayScheduleRow.get("is_active")).isEqualTo(true);
		assertThat(fixedDayScheduleRow.get("created_at")).isNotNull();
		assertThat(fixedDayScheduleRow.get("updated_at")).isNotNull();

		jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type)
				values (?, ?, ?)
			""",
			lastDayScheduleId,
			memberId,
			"last_day_of_month"
		);

		Map<String, Object> lastDayScheduleRow = jdbcTemplate.queryForMap(
			"""
				select day_of_month, trigger_delay_days, is_active, created_at, updated_at
				from member_payroll_schedules
				where id = ?
			""",
			lastDayScheduleId
		);

		assertThat(lastDayScheduleRow.get("day_of_month")).isNull();
		assertThat(((Number) lastDayScheduleRow.get("trigger_delay_days")).intValue()).isEqualTo(1);
		assertThat(lastDayScheduleRow.get("is_active")).isEqualTo(true);
		assertThat(lastDayScheduleRow.get("created_at")).isNotNull();
		assertThat(lastDayScheduleRow.get("updated_at")).isNotNull();

		jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month, trigger_delay_days)
				values (?, ?, ?, ?, ?)
			""",
			secondFixedDayScheduleId,
			memberId,
			"fixed_day_of_month",
			25,
			3
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"fixed_day_of_month",
			16
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"weekly",
			10
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type)
				values (?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"fixed_day_of_month"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"fixed_day_of_month",
			0
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"fixed_day_of_month",
			32
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"last_day_of_month",
			30
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month, trigger_delay_days)
				values (?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"fixed_day_of_month",
			5,
			-1
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month, trigger_delay_days)
				values (?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			memberId,
			"fixed_day_of_month",
			5,
			8
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month)
				values (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"fixed_day_of_month",
			10
		)).isInstanceOf(DataIntegrityViolationException.class);

		String payrollScheduleIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'uq_member_payroll_schedules_member_schedule_type_day_of_month'
			""",
			String.class
		);

		assertThat(payrollScheduleIndexDefinition).isNotNull();
		assertThat(payrollScheduleIndexDefinition.toLowerCase())
			.contains("create unique index uq_member_payroll_schedules_member_schedule_type_day_of_month")
			.contains("on public.member_payroll_schedules")
			.contains("member_id, schedule_type")
			.contains("coalesce(")
			.contains("day_of_month");
	}

	private boolean tableExists(String tableName) {
		Integer tableCount = jdbcTemplate.queryForObject(
			"""
				select count(*)
				from information_schema.tables
				where table_schema = 'public'
				  and table_name = ?
			""",
			Integer.class,
			tableName
		);

		return tableCount != null && tableCount > 0;
	}

	private UUID insertFamily(String familyName) {
		UUID familyId = UUID.randomUUID();
		jdbcTemplate.update("insert into families (id, name) values (?, ?)", familyId, familyName);
		return familyId;
	}

	private UUID insertFamilyMember(UUID familyId, String firstName) {
		UUID memberId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
				insert into family_members (id, family_id, first_name)
				values (?, ?, ?)
			""",
			memberId,
			familyId,
			firstName
		);
		return memberId;
	}

}
