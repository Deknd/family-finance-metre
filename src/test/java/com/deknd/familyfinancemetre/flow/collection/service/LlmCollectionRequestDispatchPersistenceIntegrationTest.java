package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestReason;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("migration")
class LlmCollectionRequestDispatchPersistenceIntegrationTest {

	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_collection_dispatch_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private LlmCollectionRequestDispatchService llmCollectionRequestDispatchService;

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

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute(
			"truncate table llm_collection_requests, member_payroll_schedules, family_members, families restart identity cascade"
		);
	}

	@Test
	@DisplayName("После успешного outbound вызова сохраняет accepted статус и данные ответа n8n")
	void dispatchPendingRequestPersistsAcceptedLifecycleData() throws Exception {
		LlmCollectionRequestEntity request = insertPendingRequest();
		given(n8nClient.startCollection(request.getRequestId(), request.getRequestPayload()))
			.willReturn(new N8nStartCollectionResult.Accepted(
				"accepted",
				request.getRequestId(),
				"n8n-run-001",
				OBJECT_MAPPER.readTree("""
					{
					  "status": "accepted",
					  "request_id": "99999999-9999-9999-9999-999999999999",
					  "workflow_run_id": "n8n-run-001"
					}
					""")
			));

		llmCollectionRequestDispatchService.dispatchPendingRequest(request);

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					status,
					accepted_at,
					completed_at,
					workflow_run_id,
					response_payload #>> '{workflow_run_id}' as payload_workflow_run_id,
					error_message
				from llm_collection_requests
				where id = ?
				""",
			request.getId()
		);

		assertThat(storedRow.get("status")).isEqualTo("accepted");
		assertThat(storedRow.get("accepted_at")).isNotNull();
		assertThat(storedRow.get("completed_at")).isNull();
		assertThat(storedRow.get("workflow_run_id")).isEqualTo("n8n-run-001");
		assertThat(storedRow.get("payload_workflow_run_id")).isEqualTo("n8n-run-001");
		assertThat(storedRow.get("error_message")).isNull();
	}

	@Test
	@DisplayName("После failed результата сохраняет failed статус, response_payload и error_message")
	void dispatchPendingRequestPersistsFailureLifecycleData() throws Exception {
		LlmCollectionRequestEntity request = insertPendingRequest();
		given(n8nClient.startCollection(request.getRequestId(), request.getRequestPayload()))
			.willReturn(new N8nStartCollectionResult.Failed(
				401,
				OBJECT_MAPPER.readTree("""
					{
					  "error": {
					    "code": "INVALID_TOKEN"
					  }
					}
					"""),
				"n8n вернул HTTP 401: Authorization shared secret is invalid"
			));

		llmCollectionRequestDispatchService.dispatchPendingRequest(request);

		Map<String, Object> storedRow = jdbcTemplate.queryForMap(
			"""
				select
					status,
					accepted_at,
					completed_at,
					workflow_run_id,
					response_payload #>> '{error,code}' as payload_error_code,
					error_message
				from llm_collection_requests
				where id = ?
				""",
			request.getId()
		);

		assertThat(storedRow.get("status")).isEqualTo("failed");
		assertThat(storedRow.get("accepted_at")).isNull();
		assertThat(storedRow.get("completed_at")).isNull();
		assertThat(storedRow.get("workflow_run_id")).isNull();
		assertThat(storedRow.get("payload_error_code")).isEqualTo("INVALID_TOKEN");
		assertThat(storedRow.get("error_message")).isEqualTo("n8n вернул HTTP 401: Authorization shared secret is invalid");
	}

	private LlmCollectionRequestEntity insertPendingRequest() throws Exception {
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
		memberPayrollScheduleRepository.saveAndFlush(payrollSchedule);

		LlmCollectionRequestEntity request = new LlmCollectionRequestEntity();
		request.setRequestId("99999999-9999-9999-9999-999999999999");
		request.setFamily(family);
		request.setMember(member);
		request.setPayrollSchedule(payrollSchedule);
		request.setPeriodYear(2026);
		request.setPeriodMonth((short) 3);
		request.setReason(LlmCollectionRequestReason.DAY_AFTER_SALARY);
		request.setStatus(LlmCollectionRequestStatus.PENDING);
		request.setRequestedFields(OBJECT_MAPPER.readTree(
			"[\"monthly_income\", \"monthly_expenses\", \"monthly_credit_payments\", \"liquid_savings\"]"
		));
		request.setNominalPayrollDate(LocalDate.of(2026, 3, 16));
		request.setEffectivePayrollDate(LocalDate.of(2026, 3, 16));
		request.setScheduledTriggerDate(LocalDate.of(2026, 3, 17));
		request.setTriggeredAt(OffsetDateTime.parse("2026-03-17T09:00:00+03:00"));
		request.setRequestPayload(OBJECT_MAPPER.readTree("""
			{
			  "request_id": "99999999-9999-9999-9999-999999999999",
			  "member": {
			    "id": "22222222-2222-2222-2222-222222222222"
			  }
			}
			"""));
		return llmCollectionRequestRepository.saveAndFlush(request);
	}
}
