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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("migration")
class FlywayReferenceMigrationSmokeTest {

	private static final ZoneOffset MOSCOW_OFFSET = ZoneOffset.ofHours(3);

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
		assertThat(tableExists("llm_collection_requests")).isTrue();
		assertThat(tableExists("finance_submissions")).isTrue();
		assertThat(tableExists("member_finance_snapshots")).isTrue();
		assertThat(tableExists("family_dashboard_snapshots")).isTrue();

		assertThat(migrationApplied("1")).isTrue();
		assertThat(migrationApplied("2")).isTrue();
		assertThat(migrationApplied("3")).isTrue();
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

	@Test
	void llmCollectionRequestsSupportConstraintsForeignKeysAndIndexes() {
		UUID familyId = insertFamily("Volkov family");
		UUID memberId = insertFamilyMember(familyId, "Nina");
		UUID payrollScheduleId = insertPayrollSchedule(memberId, 10);
		UUID llmCollectionRequestId = UUID.randomUUID();
		String requestId = "req-" + UUID.randomUUID();

		jdbcTemplate.update(
			"""
				insert into llm_collection_requests (
				    id,
				    request_id,
				    family_id,
				    member_id,
				    payroll_schedule_id,
				    period_year,
				    period_month,
				    reason,
				    status,
				    requested_fields,
				    nominal_payroll_date,
				    effective_payroll_date,
				    scheduled_trigger_date,
				    triggered_at,
				    request_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
			""",
			llmCollectionRequestId,
			requestId,
			familyId,
			memberId,
			payrollScheduleId,
			2026,
			3,
			"day_after_salary",
			"pending",
			"[\"monthly_income\",\"monthly_expenses\"]",
			LocalDate.parse("2026-03-10"),
			LocalDate.parse("2026-03-10"),
			LocalDate.parse("2026-03-11"),
			atMoscow("2026-03-11T09:00:00"),
			"{\"request_id\":\"req\"}"
		);

		Map<String, Object> requestRow = jdbcTemplate.queryForMap(
			"""
				select accepted_at, completed_at, workflow_run_id, response_payload, error_message, created_at, updated_at
				from llm_collection_requests
				where id = ?
			""",
			llmCollectionRequestId
		);

		assertThat(requestRow.get("accepted_at")).isNull();
		assertThat(requestRow.get("completed_at")).isNull();
		assertThat(requestRow.get("workflow_run_id")).isNull();
		assertThat(requestRow.get("response_payload")).isNull();
		assertThat(requestRow.get("error_message")).isNull();
		assertThat(requestRow.get("created_at")).isNotNull();
		assertThat(requestRow.get("updated_at")).isNotNull();

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into llm_collection_requests (
				    id,
				    request_id,
				    family_id,
				    member_id,
				    payroll_schedule_id,
				    period_year,
				    period_month,
				    reason,
				    status,
				    requested_fields,
				    nominal_payroll_date,
				    effective_payroll_date,
				    scheduled_trigger_date,
				    triggered_at,
				    request_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			requestId,
			familyId,
			memberId,
			insertPayrollSchedule(memberId, 25),
			2026,
			4,
			"day_after_salary",
			"pending",
			"[\"monthly_income\"]",
			LocalDate.parse("2026-04-25"),
			LocalDate.parse("2026-04-25"),
			LocalDate.parse("2026-04-26"),
			atMoscow("2026-04-26T09:00:00"),
			"{\"request_id\":\"duplicate\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into llm_collection_requests (
				    id,
				    request_id,
				    family_id,
				    member_id,
				    payroll_schedule_id,
				    period_year,
				    period_month,
				    reason,
				    status,
				    requested_fields,
				    nominal_payroll_date,
				    effective_payroll_date,
				    scheduled_trigger_date,
				    triggered_at,
				    request_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"req-" + UUID.randomUUID(),
			familyId,
			memberId,
			payrollScheduleId,
			2026,
			3,
			"day_after_salary",
			"accepted",
			"[\"monthly_income\"]",
			LocalDate.parse("2026-03-10"),
			LocalDate.parse("2026-03-10"),
			LocalDate.parse("2026-03-11"),
			atMoscow("2026-03-11T10:00:00"),
			"{\"request_id\":\"duplicate-schedule\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into llm_collection_requests (
				    id,
				    request_id,
				    family_id,
				    member_id,
				    payroll_schedule_id,
				    period_year,
				    period_month,
				    reason,
				    status,
				    requested_fields,
				    nominal_payroll_date,
				    effective_payroll_date,
				    scheduled_trigger_date,
				    triggered_at,
				    request_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"req-" + UUID.randomUUID(),
			familyId,
			memberId,
			insertPayrollSchedule(memberId, 22),
			2026,
			13,
			"day_after_salary",
			"pending",
			"[\"monthly_income\"]",
			LocalDate.parse("2026-05-22"),
			LocalDate.parse("2026-05-22"),
			LocalDate.parse("2026-05-23"),
			atMoscow("2026-05-23T09:00:00"),
			"{\"request_id\":\"bad-month\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into llm_collection_requests (
				    id,
				    request_id,
				    family_id,
				    member_id,
				    payroll_schedule_id,
				    period_year,
				    period_month,
				    reason,
				    status,
				    requested_fields,
				    nominal_payroll_date,
				    effective_payroll_date,
				    scheduled_trigger_date,
				    triggered_at,
				    request_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"req-" + UUID.randomUUID(),
			familyId,
			memberId,
			insertPayrollSchedule(memberId, 28),
			2026,
			5,
			"day_after_salary",
			"queued",
			"[\"monthly_income\"]",
			LocalDate.parse("2026-05-28"),
			LocalDate.parse("2026-05-28"),
			LocalDate.parse("2026-05-29"),
			atMoscow("2026-05-29T09:00:00"),
			"{\"request_id\":\"bad-status\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into llm_collection_requests (
				    id,
				    request_id,
				    family_id,
				    member_id,
				    payroll_schedule_id,
				    period_year,
				    period_month,
				    reason,
				    status,
				    requested_fields,
				    nominal_payroll_date,
				    effective_payroll_date,
				    scheduled_trigger_date,
				    triggered_at,
				    request_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"req-" + UUID.randomUUID(),
			UUID.randomUUID(),
			memberId,
			insertPayrollSchedule(memberId, 30),
			2026,
			6,
			"day_after_salary",
			"pending",
			"[\"monthly_income\"]",
			LocalDate.parse("2026-06-30"),
			LocalDate.parse("2026-06-30"),
			LocalDate.parse("2026-07-01"),
			atMoscow("2026-07-01T09:00:00"),
			"{\"request_id\":\"bad-fk\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		String requestIdConstraint = jdbcTemplate.queryForObject(
			"""
				select constraint_name
				from information_schema.table_constraints
				where table_schema = 'public'
				  and table_name = 'llm_collection_requests'
				  and constraint_type = 'UNIQUE'
				  and constraint_name = 'uq_llm_collection_requests_request_id'
			""",
			String.class
		);
		String requestStatusIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'idx_llm_collection_requests_status'
			""",
			String.class
		);
		String payrollEventUniqueIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'uq_llm_requests_schedule_effective_date'
			""",
			String.class
		);

		assertThat(requestIdConstraint).isEqualTo("uq_llm_collection_requests_request_id");
		assertThat(requestStatusIndexDefinition).isNotNull();
		assertThat(requestStatusIndexDefinition.toLowerCase())
			.contains("create index idx_llm_collection_requests_status")
			.contains("on public.llm_collection_requests")
			.contains("(status)");
		assertThat(payrollEventUniqueIndexDefinition).isNotNull();
		assertThat(payrollEventUniqueIndexDefinition.toLowerCase())
			.contains("create unique index uq_llm_requests_schedule_effective_date")
			.contains("on public.llm_collection_requests")
			.contains("payroll_schedule_id, effective_payroll_date");
	}

	@Test
	void financeSubmissionsSupportConstraintsForeignKeysAndIndexes() {
		UUID familyId = insertFamily("Belov family");
		UUID memberId = insertFamilyMember(familyId, "Ilya");
		UUID payrollScheduleId = insertPayrollSchedule(memberId, 12);
		UUID llmRequestId = insertLlmCollectionRequest(familyId, memberId, payrollScheduleId, 2026, 3, 12);
		UUID submissionId = UUID.randomUUID();
		String externalSubmissionId = "sub-" + UUID.randomUUID();

		jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    request_id,
				    llm_collection_request_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    confidence,
				    notes,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			submissionId,
			externalSubmissionId,
			"req-" + UUID.randomUUID(),
			llmRequestId,
			familyId,
			memberId,
			"telegram",
			2026,
			3,
			atMoscow("2026-03-15T08:40:00"),
			120000,
			50000,
			18000,
			150000,
			"medium",
			"Approximate values",
			"{\"source\":\"telegram\"}"
		);

		Map<String, Object> submissionRow = jdbcTemplate.queryForMap(
			"""
				select created_at, notes, confidence, request_id, llm_collection_request_id
				from finance_submissions
				where id = ?
			""",
			submissionId
		);

		assertThat(submissionRow.get("created_at")).isNotNull();
		assertThat(submissionRow.get("notes")).isEqualTo("Approximate values");
		assertThat(submissionRow.get("confidence")).isEqualTo("medium");
		assertThat(submissionRow.get("request_id")).isNotNull();
		assertThat(submissionRow.get("llm_collection_request_id")).isEqualTo(llmRequestId);

		jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"sub-" + UUID.randomUUID(),
			familyId,
			memberId,
			"telegram",
			2026,
			4,
			atMoscow("2026-04-15T08:40:00"),
			100000,
			40000,
			10000,
			90000,
			"{\"source\":\"telegram\",\"variant\":\"minimal\"}"
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			externalSubmissionId,
			familyId,
			memberId,
			"telegram",
			2026,
			5,
			atMoscow("2026-05-15T08:40:00"),
			100000,
			40000,
			10000,
			90000,
			"{\"source\":\"telegram\",\"variant\":\"duplicate\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"sub-" + UUID.randomUUID(),
			familyId,
			memberId,
			"email",
			2026,
			5,
			atMoscow("2026-05-15T08:40:00"),
			100000,
			40000,
			10000,
			90000,
			"{\"source\":\"email\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    confidence,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"sub-" + UUID.randomUUID(),
			familyId,
			memberId,
			"telegram",
			2026,
			5,
			atMoscow("2026-05-15T08:40:00"),
			100000,
			40000,
			10000,
			90000,
			"certain",
			"{\"confidence\":\"certain\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"sub-" + UUID.randomUUID(),
			familyId,
			memberId,
			"telegram",
			2026,
			0,
			atMoscow("2026-05-15T08:40:00"),
			100000,
			40000,
			10000,
			90000,
			"{\"period_month\":0}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"sub-" + UUID.randomUUID(),
			familyId,
			memberId,
			"telegram",
			2026,
			5,
			atMoscow("2026-05-15T08:40:00"),
			-1,
			40000,
			10000,
			90000,
			"{\"monthly_income\":-1}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    llm_collection_request_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			UUID.randomUUID(),
			"sub-" + UUID.randomUUID(),
			UUID.randomUUID(),
			familyId,
			memberId,
			"telegram",
			2026,
			5,
			atMoscow("2026-05-15T08:40:00"),
			100000,
			40000,
			10000,
			90000,
			"{\"llm_collection_request_id\":\"missing\"}"
		)).isInstanceOf(DataIntegrityViolationException.class);

		String memberPeriodIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'idx_finance_submissions_member_period_collected_at'
			""",
			String.class
		);
		String requestIdIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'idx_finance_submissions_request_id'
			""",
			String.class
		);

		assertThat(memberPeriodIndexDefinition).isNotNull();
		assertThat(memberPeriodIndexDefinition.toLowerCase())
			.contains("create index idx_finance_submissions_member_period_collected_at")
			.contains("on public.finance_submissions")
			.contains("member_id, period_year, period_month, collected_at desc");
		assertThat(requestIdIndexDefinition).isNotNull();
		assertThat(requestIdIndexDefinition.toLowerCase())
			.contains("create index idx_finance_submissions_request_id")
			.contains("on public.finance_submissions")
			.contains("(request_id)");
	}

	@Test
	void memberFinanceSnapshotsSupportConstraintsForeignKeysAndUniquePeriod() {
		UUID familyId = insertFamily("Morozov family");
		UUID memberId = insertFamilyMember(familyId, "Olga");
		UUID payrollScheduleId = insertPayrollSchedule(memberId, 18);
		UUID llmRequestId = insertLlmCollectionRequest(familyId, memberId, payrollScheduleId, 2026, 3, 18);
		UUID submissionId = insertFinanceSubmission(familyId, memberId, llmRequestId, "sub-" + UUID.randomUUID(), 2026, 3, 100000);
		UUID snapshotId = UUID.randomUUID();

		jdbcTemplate.update(
			"""
				insert into member_finance_snapshots (
				    id,
				    family_id,
				    member_id,
				    period_year,
				    period_month,
				    source_submission_id,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    collected_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			snapshotId,
			familyId,
			memberId,
			2026,
			3,
			submissionId,
			100000,
			40000,
			15000,
			80000,
			atMoscow("2026-03-19T10:00:00")
		);

		Map<String, Object> snapshotRow = jdbcTemplate.queryForMap(
			"""
				select created_at, updated_at
				from member_finance_snapshots
				where id = ?
			""",
			snapshotId
		);

		assertThat(snapshotRow.get("created_at")).isNotNull();
		assertThat(snapshotRow.get("updated_at")).isNotNull();

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_finance_snapshots (
				    id,
				    family_id,
				    member_id,
				    period_year,
				    period_month,
				    source_submission_id,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    collected_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			memberId,
			2026,
			3,
			submissionId,
			100000,
			40000,
			15000,
			80000,
			atMoscow("2026-03-19T10:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_finance_snapshots (
				    id,
				    family_id,
				    member_id,
				    period_year,
				    period_month,
				    source_submission_id,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    collected_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			memberId,
			2026,
			0,
			submissionId,
			100000,
			40000,
			15000,
			80000,
			atMoscow("2026-03-19T10:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_finance_snapshots (
				    id,
				    family_id,
				    member_id,
				    period_year,
				    period_month,
				    source_submission_id,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    collected_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			memberId,
			2026,
			4,
			submissionId,
			100000,
			-10,
			15000,
			80000,
			atMoscow("2026-04-19T10:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
				insert into member_finance_snapshots (
				    id,
				    family_id,
				    member_id,
				    period_year,
				    period_month,
				    source_submission_id,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    collected_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			memberId,
			2026,
			4,
			UUID.randomUUID(),
			100000,
			40000,
			15000,
			80000,
			atMoscow("2026-04-19T10:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		String snapshotUniqueIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'uq_member_finance_snapshots_member_period'
			""",
			String.class
		);

		assertThat(snapshotUniqueIndexDefinition).isNotNull();
		assertThat(snapshotUniqueIndexDefinition.toLowerCase())
			.contains("create unique index uq_member_finance_snapshots_member_period")
			.contains("on public.member_finance_snapshots")
			.contains("member_id, period_year, period_month");
	}

	@Test
	void familyDashboardSnapshotsSupportConstraintsForeignKeysAndUniquePeriod() {
		UUID familyId = insertFamily("Fedorov family");
		UUID snapshotId = UUID.randomUUID();

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
				    calculated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			snapshotId,
			familyId,
			2026,
			3,
			"warning",
			"Внимание",
			"Подушка ниже комфортной зоны",
			210000,
			90000,
			new BigDecimal("27.00"),
			new BigDecimal("4.20"),
			2,
			atMoscow("2026-03-15T09:00:00")
		);

		Map<String, Object> dashboardRow = jdbcTemplate.queryForMap(
			"""
				select created_at, updated_at
				from family_dashboard_snapshots
				where id = ?
			""",
			snapshotId
		);

		assertThat(dashboardRow.get("created_at")).isNotNull();
		assertThat(dashboardRow.get("updated_at")).isNotNull();

		assertThatThrownBy(() -> jdbcTemplate.update(
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
				    calculated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			2026,
			3,
			"normal",
			"Норма",
			"Дубликат периода",
			200000,
			80000,
			new BigDecimal("20.00"),
			new BigDecimal("5.00"),
			2,
			atMoscow("2026-03-16T09:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
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
				    calculated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			2026,
			13,
			"normal",
			"Норма",
			"Неверный месяц",
			200000,
			80000,
			new BigDecimal("20.00"),
			new BigDecimal("5.00"),
			2,
			atMoscow("2026-04-16T09:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
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
				    calculated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			2026,
			4,
			"critical",
			"Критично",
			"Неверный статус",
			200000,
			80000,
			new BigDecimal("20.00"),
			new BigDecimal("5.00"),
			2,
			atMoscow("2026-04-16T09:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
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
				    calculated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			familyId,
			2026,
			4,
			"risk",
			"Риск",
			"Неверный счетчик",
			200000,
			80000,
			new BigDecimal("20.00"),
			new BigDecimal("5.00"),
			-1,
			atMoscow("2026-04-16T09:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
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
				    calculated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			UUID.randomUUID(),
			2026,
			4,
			"risk",
			"Риск",
			"Неверная семья",
			200000,
			80000,
			new BigDecimal("20.00"),
			new BigDecimal("5.00"),
			1,
			atMoscow("2026-04-16T09:00:00")
		)).isInstanceOf(DataIntegrityViolationException.class);

		String dashboardUniqueIndexDefinition = jdbcTemplate.queryForObject(
			"""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = 'uq_family_dashboard_snapshots_family_period'
			""",
			String.class
		);

		assertThat(dashboardUniqueIndexDefinition).isNotNull();
		assertThat(dashboardUniqueIndexDefinition.toLowerCase())
			.contains("create unique index uq_family_dashboard_snapshots_family_period")
			.contains("on public.family_dashboard_snapshots")
			.contains("family_id, period_year, period_month");
	}

	private Boolean migrationApplied(String version) {
		return jdbcTemplate.queryForObject(
			"""
				select success
				from flyway_schema_history
				where version = ?
			""",
			Boolean.class,
			version
		);
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

	private UUID insertPayrollSchedule(UUID memberId, int dayOfMonth) {
		UUID payrollScheduleId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
				insert into member_payroll_schedules (id, member_id, schedule_type, day_of_month)
				values (?, ?, ?, ?)
			""",
			payrollScheduleId,
			memberId,
			"fixed_day_of_month",
			dayOfMonth
		);
		return payrollScheduleId;
	}

	private UUID insertLlmCollectionRequest(
		UUID familyId,
		UUID memberId,
		UUID payrollScheduleId,
		int periodYear,
		int periodMonth,
		int dayOfMonth
	) {
		UUID requestId = UUID.randomUUID();
		LocalDate payrollDate = LocalDate.of(periodYear, periodMonth, dayOfMonth);
		jdbcTemplate.update(
			"""
				insert into llm_collection_requests (
				    id,
				    request_id,
				    family_id,
				    member_id,
				    payroll_schedule_id,
				    period_year,
				    period_month,
				    reason,
				    status,
				    requested_fields,
				    nominal_payroll_date,
				    effective_payroll_date,
				    scheduled_trigger_date,
				    triggered_at,
				    request_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
			""",
			requestId,
			"req-" + UUID.randomUUID(),
			familyId,
			memberId,
			payrollScheduleId,
			periodYear,
			periodMonth,
			"day_after_salary",
			"pending",
			"[\"monthly_income\",\"monthly_expenses\",\"monthly_credit_payments\",\"liquid_savings\"]",
			payrollDate,
			payrollDate,
			payrollDate.plusDays(1),
			payrollDate.plusDays(1).atTime(9, 0).atOffset(MOSCOW_OFFSET),
			"{\"request_id\":\"seed-request\"}"
		);
		return requestId;
	}

	private UUID insertFinanceSubmission(
		UUID familyId,
		UUID memberId,
		UUID llmCollectionRequestId,
		String externalSubmissionId,
		int periodYear,
		int periodMonth,
		int monthlyIncome
	) {
		UUID submissionId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
				insert into finance_submissions (
				    id,
				    external_submission_id,
				    llm_collection_request_id,
				    family_id,
				    member_id,
				    source,
				    period_year,
				    period_month,
				    collected_at,
				    monthly_income,
				    monthly_expenses,
				    monthly_credit_payments,
				    liquid_savings,
				    raw_payload
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
			""",
			submissionId,
			externalSubmissionId,
			llmCollectionRequestId,
			familyId,
			memberId,
			"telegram",
			periodYear,
			periodMonth,
			OffsetDateTime.of(periodYear, periodMonth, 20, 9, 0, 0, 0, MOSCOW_OFFSET),
			monthlyIncome,
			40000,
			10000,
			90000,
			"{\"external_submission_id\":\"" + externalSubmissionId + "\"}"
		);
		return submissionId;
	}

	private OffsetDateTime atMoscow(String localDateTime) {
		return OffsetDateTime.parse(localDateTime + "+03:00");
	}

}
