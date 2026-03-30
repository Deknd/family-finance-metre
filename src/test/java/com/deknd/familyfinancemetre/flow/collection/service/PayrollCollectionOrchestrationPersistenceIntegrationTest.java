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
import com.deknd.familyfinancemetre.flow.collection.client.N8nClient;
import com.deknd.familyfinancemetre.flow.collection.client.N8nStartCollectionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("migration")
class PayrollCollectionOrchestrationPersistenceIntegrationTest {

	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
	private static final ZoneId APPLICATION_ZONE = ZoneId.of("Europe/Moscow");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_payroll_scheduler_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private PayrollCollectionOrchestrationService payrollCollectionOrchestrationService;

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

	@MockitoBean
	private N8nClient n8nClient;

	@MockitoBean
	private Clock clock;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute(
			"truncate table llm_collection_requests, member_payroll_schedules, family_members, families restart identity cascade"
		);
		reset(n8nClient, clock);
	}

	@Test
	@DisplayName("Создает llm_collection_request, переводит его в accepted и вызывает n8n для due payroll события")
	void runDailyPayrollCollectionCreatesAndDispatchesDuePayrollEvent() {
		useCurrentInstant(Instant.parse("2026-03-17T06:00:00Z"));
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1, PayrollScheduleType.FIXED_DAY_OF_MONTH, true, true);
		stubAcceptedN8nResponse("n8n-run-001");

		payrollCollectionOrchestrationService.runDailyPayrollCollection();

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					status,
					effective_payroll_date,
					scheduled_trigger_date,
					workflow_run_id,
					response_payload #>> '{workflow_run_id}' as payload_workflow_run_id
				from llm_collection_requests
				"""
		);

		assertThat(llmCollectionRequestRepository.findAll()).hasSize(1);
		assertThat(storedRow.get("status")).isEqualTo("accepted");
		assertThat(storedRow.get("effective_payroll_date").toString()).startsWith("2026-03-16");
		assertThat(storedRow.get("scheduled_trigger_date").toString()).startsWith("2026-03-17");
		assertThat(storedRow.get("workflow_run_id")).isEqualTo("n8n-run-001");
		assertThat(storedRow.get("payload_workflow_run_id")).isEqualTo("n8n-run-001");
		verify(n8nClient, times(1)).startCollection(anyString(), any());
	}

	@Test
	@DisplayName("Использует дату в таймзоне семьи, даже если она отличается от app.timezone")
	void runDailyPayrollCollectionUsesFamilyTimezoneInsteadOfApplicationTimezone() {
		useCurrentInstant(Instant.parse("2026-03-31T22:30:00Z"));
		insertPayrollSchedule("America/New_York", (short) 31, (short) 0, PayrollScheduleType.FIXED_DAY_OF_MONTH, true, true);
		stubAcceptedN8nResponse("n8n-run-ny-001");

		payrollCollectionOrchestrationService.runDailyPayrollCollection();

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					status,
					effective_payroll_date,
					scheduled_trigger_date
				from llm_collection_requests
				"""
		);

		assertThat(llmCollectionRequestRepository.findAll()).hasSize(1);
		assertThat(storedRow.get("status")).isEqualTo("accepted");
		assertThat(storedRow.get("effective_payroll_date").toString()).startsWith("2026-03-31");
		assertThat(storedRow.get("scheduled_trigger_date").toString()).startsWith("2026-03-31");
		verify(n8nClient, times(1)).startCollection(anyString(), any());
	}

	@Test
	@DisplayName("Повторный daily run не создает дубль llm_collection_request и не вызывает n8n второй раз")
	void runDailyPayrollCollectionDoesNotDuplicateRequestOnSecondRun() {
		useCurrentInstant(Instant.parse("2026-03-17T06:00:00Z"));
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1, PayrollScheduleType.FIXED_DAY_OF_MONTH, true, true);
		stubAcceptedN8nResponse("n8n-run-001");

		payrollCollectionOrchestrationService.runDailyPayrollCollection();
		payrollCollectionOrchestrationService.runDailyPayrollCollection();

		Integer storedRequestsCount = jdbcTemplate.queryForObject("select count(*) from llm_collection_requests", Integer.class);
		String storedStatus = jdbcTemplate.queryForObject("select status from llm_collection_requests", String.class);

		assertThat(storedRequestsCount).isEqualTo(1);
		assertThat(storedStatus).isEqualTo("accepted");
		verify(n8nClient, times(1)).startCollection(anyString(), any());
	}

	@Test
	@DisplayName("Обрабатывает только активные payroll правила активных участников из активных семей")
	void runDailyPayrollCollectionProcessesOnlyEligibleSchedules() {
		useCurrentInstant(Instant.parse("2026-03-17T06:00:00Z"));
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1, PayrollScheduleType.FIXED_DAY_OF_MONTH, true, true);
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1, PayrollScheduleType.FIXED_DAY_OF_MONTH, false, true);
		insertPayrollSchedule("Europe/Moscow", (short) 16, (short) 1, PayrollScheduleType.FIXED_DAY_OF_MONTH, true, false);
		insertPayrollSchedule(
			"Europe/Moscow",
			(short) 16,
			(short) 1,
			PayrollScheduleType.FIXED_DAY_OF_MONTH,
			true,
			true,
			FamilyStatus.ARCHIVED
		);
		stubAcceptedN8nResponse("n8n-run-001");

		payrollCollectionOrchestrationService.runDailyPayrollCollection();

		Integer storedRequestsCount = jdbcTemplate.queryForObject("select count(*) from llm_collection_requests", Integer.class);

		assertThat(storedRequestsCount).isEqualTo(1);
		verify(n8nClient, times(1)).startCollection(anyString(), any());
	}

	private void useCurrentInstant(Instant instant) {
		given(clock.getZone()).willReturn(APPLICATION_ZONE);
		given(clock.instant()).willReturn(instant);
		given(clock.withZone(any(ZoneId.class)))
			.willAnswer(invocation -> Clock.fixed(instant, invocation.getArgument(0, ZoneId.class)));
	}

	private void stubAcceptedN8nResponse(String workflowRunId) {
		given(n8nClient.startCollection(anyString(), any())).willAnswer(invocation -> {
			String requestId = invocation.getArgument(0, String.class);
			ObjectNode responsePayload = OBJECT_MAPPER.createObjectNode();
			responsePayload.put("status", "accepted");
			responsePayload.put("request_id", requestId);
			responsePayload.put("workflow_run_id", workflowRunId);
			return new N8nStartCollectionResult.Accepted(
				"accepted",
				requestId,
				workflowRunId,
				responsePayload
			);
		});
	}

	private MemberPayrollScheduleEntity insertPayrollSchedule(
		String familyTimezone,
		Short dayOfMonth,
		Short triggerDelayDays,
		PayrollScheduleType payrollScheduleType,
		boolean memberActive,
		boolean scheduleActive
	) {
		return insertPayrollSchedule(
			familyTimezone,
			dayOfMonth,
			triggerDelayDays,
			payrollScheduleType,
			memberActive,
			scheduleActive,
			FamilyStatus.ACTIVE
		);
	}

	private MemberPayrollScheduleEntity insertPayrollSchedule(
		String familyTimezone,
		Short dayOfMonth,
		Short triggerDelayDays,
		PayrollScheduleType payrollScheduleType,
		boolean memberActive,
		boolean scheduleActive,
		FamilyStatus familyStatus
	) {
		FamilyEntity family = new FamilyEntity();
		family.setName("Ivanov family");
		family.setTimezone(familyTimezone);
		family.setCurrencyCode("RUB");
		family.setStatus(familyStatus);
		familyRepository.saveAndFlush(family);

		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setFamily(family);
		member.setFirstName("Anna");
		member.setLastName("Ivanova");
		member.setDisplayName("Anna");
		member.setTelegramChatId("chat-" + UUID.randomUUID());
		member.setTelegramUsername("anna_ivanova");
		member.setActive(memberActive);
		familyMemberRepository.saveAndFlush(member);

		MemberPayrollScheduleEntity payrollSchedule = new MemberPayrollScheduleEntity();
		payrollSchedule.setMember(member);
		payrollSchedule.setLabel("Main salary");
		payrollSchedule.setScheduleType(payrollScheduleType);
		payrollSchedule.setDayOfMonth(dayOfMonth);
		payrollSchedule.setTriggerDelayDays(triggerDelayDays);
		payrollSchedule.setActive(scheduleActive);
		return memberPayrollScheduleRepository.saveAndFlush(payrollSchedule);
	}
}
