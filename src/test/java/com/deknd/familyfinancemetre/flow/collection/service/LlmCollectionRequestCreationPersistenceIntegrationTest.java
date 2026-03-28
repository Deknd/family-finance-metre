package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.family.enums.FamilyStatus;
import com.deknd.familyfinancemetre.core.family.repository.FamilyMemberRepository;
import com.deknd.familyfinancemetre.core.family.repository.FamilyRepository;
import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import com.deknd.familyfinancemetre.core.payroll.repository.MemberPayrollScheduleRepository;
import com.deknd.familyfinancemetre.core.payroll.service.PayrollEventCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("migration")
class LlmCollectionRequestCreationPersistenceIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_collection_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private LlmCollectionRequestCreationService llmCollectionRequestCreationService;

	@Autowired
	private FamilyRepository familyRepository;

	@Autowired
	private FamilyMemberRepository familyMemberRepository;

	@Autowired
	private MemberPayrollScheduleRepository memberPayrollScheduleRepository;

	@Autowired
	private LlmCollectionRequestRepository llmCollectionRequestRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute(
			"truncate table llm_collection_requests, member_payroll_schedules, family_members, families restart identity cascade"
		);
	}

	@Test
	@DisplayName("Создает запись llm_collection_requests с pending статусом и полным request_payload")
	void createPendingRequestPersistsStoredPayload() {
		MemberPayrollScheduleEntity payrollSchedule = insertPayrollSchedule();
		PayrollEventCalculationResult payrollEvent = payrollEvent();
		UUID familyId = payrollSchedule.getMember().getFamily().getId();
		UUID memberId = payrollSchedule.getMember().getId();

		Optional<?> createdRequest = llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, payrollEvent);

		assertThat(createdRequest).isPresent();

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					request_id,
					family_id::text as family_id,
					member_id::text as member_id,
					payroll_schedule_id::text as payroll_schedule_id,
					period_year,
					period_month,
					reason,
					status,
					requested_fields::text as requested_fields,
					nominal_payroll_date,
					effective_payroll_date,
					scheduled_trigger_date,
					request_payload ->> 'request_id' as payload_request_id,
					request_payload #>> '{member,name}' as payload_member_name,
					request_payload #>> '{member,telegram_chat_id}' as payload_member_chat_id,
					request_payload #>> '{collection_scope,fields}' as payload_fields,
					request_payload #>> '{callback,submit_url}' as payload_submit_url
				from llm_collection_requests
				""");

		assertThat(UUID.fromString(storedRow.get("request_id").toString())).isNotNull();
		assertThat(storedRow.get("family_id")).isEqualTo(familyId.toString());
		assertThat(storedRow.get("member_id")).isEqualTo(memberId.toString());
		assertThat(storedRow.get("payroll_schedule_id")).isEqualTo(payrollSchedule.getId().toString());
		assertThat(storedRow.get("period_year")).isEqualTo(2026);
		assertThat(((Number) storedRow.get("period_month")).shortValue()).isEqualTo((short) 3);
		assertThat(storedRow.get("reason")).isEqualTo("day_after_salary");
		assertThat(storedRow.get("status")).isEqualTo("pending");
		assertThat(storedRow.get("requested_fields")).isEqualTo(
			"[\"monthly_income\", \"monthly_expenses\", \"monthly_credit_payments\", \"liquid_savings\"]"
		);
		assertThat(storedRow.get("nominal_payroll_date").toString()).startsWith("2026-03-16");
		assertThat(storedRow.get("effective_payroll_date").toString()).startsWith("2026-03-16");
		assertThat(storedRow.get("scheduled_trigger_date").toString()).startsWith("2026-03-17");
		assertThat(storedRow.get("payload_request_id")).isEqualTo(storedRow.get("request_id"));
		assertThat(storedRow.get("payload_member_name")).isEqualTo("Anna");
		assertThat(storedRow.get("payload_member_chat_id")).isEqualTo("123456789");
		assertThat(storedRow.get("payload_fields"))
			.isEqualTo("[\"monthly_income\", \"monthly_expenses\", \"monthly_credit_payments\", \"liquid_savings\"]");
		assertThat(storedRow.get("payload_submit_url"))
			.isEqualTo("https://server.migration.local/api/v1/intake/user-finance-data");
	}

	@Test
	@DisplayName("Повторное создание для того же payroll-события не добавляет вторую строку")
	void createPendingRequestSuppressesDuplicatePayrollEvent() {
		MemberPayrollScheduleEntity payrollSchedule = insertPayrollSchedule();
		PayrollEventCalculationResult payrollEvent = payrollEvent();

		Optional<?> firstRequest = llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, payrollEvent);
		Optional<?> secondRequest = llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, payrollEvent);

		Integer storedRequestsCount = jdbcTemplate.queryForObject(
			"select count(*) from llm_collection_requests where payroll_schedule_id = ? and effective_payroll_date = ?",
			Integer.class,
			payrollSchedule.getId(),
			LocalDate.of(2026, 3, 16)
		);

		assertThat(firstRequest).isPresent();
		assertThat(secondRequest).isEmpty();
		assertThat(storedRequestsCount).isEqualTo(1);
		assertThat(llmCollectionRequestRepository.findAll()).hasSize(1);
	}

	private MemberPayrollScheduleEntity insertPayrollSchedule() {
		FamilyEntity family = new FamilyEntity();
		family.setName("Ivanov family");
		family.setTimezone("Europe/Moscow");
		family.setCurrencyCode("RUB");
		family.setStatus(FamilyStatus.ACTIVE);
		familyRepository.saveAndFlush(family);

		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setFamily(family);
		member.setFirstName("Anna");
		member.setLastName("Ivanova");
		member.setDisplayName("Anna");
		member.setTelegramChatId("123456789");
		member.setTelegramUsername("anna_ivanova");
		member.setActive(true);
		familyMemberRepository.saveAndFlush(member);

		MemberPayrollScheduleEntity payrollSchedule = new MemberPayrollScheduleEntity();
		payrollSchedule.setMember(member);
		payrollSchedule.setLabel("Main salary");
		payrollSchedule.setScheduleType(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		payrollSchedule.setDayOfMonth((short) 16);
		payrollSchedule.setTriggerDelayDays((short) 1);
		payrollSchedule.setActive(true);
		return memberPayrollScheduleRepository.saveAndFlush(payrollSchedule);
	}

	private PayrollEventCalculationResult payrollEvent() {
		return new PayrollEventCalculationResult(
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 17),
			2026,
			(short) 3
		);
	}
}
