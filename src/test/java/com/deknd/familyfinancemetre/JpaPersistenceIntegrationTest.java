package com.deknd.familyfinancemetre;

import com.deknd.familyfinancemetre.core.device.entity.DeviceEntity;
import com.deknd.familyfinancemetre.core.snapshot.entity.FamilyDashboardSnapshotEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.snapshot.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.snapshot.entity.MemberFinanceSnapshotEntity;
import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.snapshot.enums.DashboardStatus;
import com.deknd.familyfinancemetre.core.device.enums.DeviceStatus;
import com.deknd.familyfinancemetre.core.family.enums.FamilyStatus;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestReason;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import com.deknd.familyfinancemetre.core.snapshot.enums.SubmissionConfidence;
import com.deknd.familyfinancemetre.core.snapshot.enums.SubmissionSource;
import com.deknd.familyfinancemetre.core.device.repository.DeviceRepository;
import com.deknd.familyfinancemetre.core.snapshot.repository.FamilyDashboardSnapshotRepository;
import com.deknd.familyfinancemetre.core.family.repository.FamilyMemberRepository;
import com.deknd.familyfinancemetre.core.family.repository.FamilyRepository;
import com.deknd.familyfinancemetre.core.snapshot.repository.FinanceSubmissionRepository;
import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.deknd.familyfinancemetre.core.snapshot.repository.MemberFinanceSnapshotRepository;
import com.deknd.familyfinancemetre.core.payroll.repository.MemberPayrollScheduleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Transactional
@ActiveProfiles("migration")
class JpaPersistenceIntegrationTest {

	private static final ZoneOffset MOSCOW_OFFSET = ZoneOffset.ofHours(3);

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("family_finance_metre_jpa_test")
		.withUsername("test_user")
		.withPassword("test_password");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private FamilyRepository familyRepository;

	@Autowired
	private FamilyMemberRepository familyMemberRepository;

	@Autowired
	private DeviceRepository deviceRepository;

	@Autowired
	private MemberPayrollScheduleRepository memberPayrollScheduleRepository;

	@Autowired
	private LlmCollectionRequestRepository llmCollectionRequestRepository;

	@Autowired
	private FinanceSubmissionRepository financeSubmissionRepository;

	@Autowired
	private MemberFinanceSnapshotRepository memberFinanceSnapshotRepository;

	@Autowired
	private FamilyDashboardSnapshotRepository familyDashboardSnapshotRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void persistsReferenceEntitiesAndPopulatesAuditFields() {
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

		DeviceEntity device = new DeviceEntity();
		device.setFamily(family);
		device.setName("Hall display");
		device.setDeviceTokenHash("hash-123");
		device.setStatus(DeviceStatus.ACTIVE);
		device.setLastSeenAt(atMoscow("2026-03-15T09:00:00"));
		deviceRepository.saveAndFlush(device);

		MemberPayrollScheduleEntity payrollSchedule = new MemberPayrollScheduleEntity();
		payrollSchedule.setMember(member);
		payrollSchedule.setLabel("Main salary");
		payrollSchedule.setScheduleType(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		payrollSchedule.setDayOfMonth((short) 16);
		payrollSchedule.setTriggerDelayDays((short) 1);
		payrollSchedule.setActive(true);
		memberPayrollScheduleRepository.saveAndFlush(payrollSchedule);

		entityManager.clear();

		FamilyEntity storedFamily = familyRepository.findById(family.getId()).orElseThrow();
		FamilyMemberEntity storedMember = familyMemberRepository.findById(member.getId()).orElseThrow();
		DeviceEntity storedDevice = deviceRepository.findById(device.getId()).orElseThrow();
		MemberPayrollScheduleEntity storedSchedule = memberPayrollScheduleRepository.findById(payrollSchedule.getId()).orElseThrow();

		assertThat(storedFamily.getId()).isNotNull();
		assertThat(storedFamily.getCreatedAt()).isNotNull();
		assertThat(storedFamily.getUpdatedAt()).isNotNull();
		assertThat(storedFamily.getStatus()).isEqualTo(FamilyStatus.ACTIVE);

		assertThat(storedMember.getId()).isNotNull();
		assertThat(storedMember.getCreatedAt()).isNotNull();
		assertThat(storedMember.getUpdatedAt()).isNotNull();
		assertThat(storedMember.getActive()).isTrue();

		assertThat(storedDevice.getId()).isNotNull();
		assertThat(storedDevice.getCreatedAt()).isNotNull();
		assertThat(storedDevice.getUpdatedAt()).isNotNull();
		assertThat(storedDevice.getStatus()).isEqualTo(DeviceStatus.ACTIVE);

		assertThat(storedSchedule.getId()).isNotNull();
		assertThat(storedSchedule.getCreatedAt()).isNotNull();
		assertThat(storedSchedule.getUpdatedAt()).isNotNull();
		assertThat(storedSchedule.getScheduleType()).isEqualTo(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		assertThat(storedSchedule.getTriggerDelayDays()).isEqualTo((short) 1);

		assertThat(jdbcTemplate.queryForObject(
			"select status from families where id = ?",
			String.class,
			family.getId()
		)).isEqualTo("active");
		assertThat(jdbcTemplate.queryForObject(
			"select status from devices where id = ?",
			String.class,
			device.getId()
		)).isEqualTo("active");
		assertThat(jdbcTemplate.queryForObject(
			"select schedule_type from member_payroll_schedules where id = ?",
			String.class,
			payrollSchedule.getId()
		)).isEqualTo("fixed_day_of_month");
	}

	@Test
	void persistsIntegrationEntitiesWithJsonbAndEnums() {
		FamilyEntity family = saveFamily();
		FamilyMemberEntity member = saveMember(family);
		MemberPayrollScheduleEntity payrollSchedule = savePayrollSchedule(member);

		JsonNode requestedFields = objectMapper.createArrayNode()
			.add("monthly_income")
			.add("monthly_expenses")
			.add("monthly_credit_payments")
			.add("liquid_savings");
		JsonNode requestPayload = objectMapper.createObjectNode()
			.put("request_id", "req-2026-03-15-anna")
			.put("member_id", member.getId().toString());
		JsonNode responsePayload = objectMapper.createObjectNode()
			.put("status", "accepted")
			.put("workflow_run_id", "n8n-run-001");

		LlmCollectionRequestEntity llmRequest = new LlmCollectionRequestEntity();
		llmRequest.setRequestId("req-2026-03-15-anna");
		llmRequest.setFamily(family);
		llmRequest.setMember(member);
		llmRequest.setPayrollSchedule(payrollSchedule);
		llmRequest.setPeriodYear(2026);
		llmRequest.setPeriodMonth((short) 3);
		llmRequest.setReason(LlmCollectionRequestReason.DAY_AFTER_SALARY);
		llmRequest.setStatus(LlmCollectionRequestStatus.ACCEPTED);
		llmRequest.setRequestedFields(requestedFields);
		llmRequest.setNominalPayrollDate(LocalDate.of(2026, 3, 16));
		llmRequest.setEffectivePayrollDate(LocalDate.of(2026, 3, 16));
		llmRequest.setScheduledTriggerDate(LocalDate.of(2026, 3, 17));
		llmRequest.setTriggeredAt(atMoscow("2026-03-17T09:00:00"));
		llmRequest.setAcceptedAt(atMoscow("2026-03-17T09:00:05"));
		llmRequest.setWorkflowRunId("n8n-run-001");
		llmRequest.setRequestPayload(requestPayload);
		llmRequest.setResponsePayload(responsePayload);
		llmCollectionRequestRepository.saveAndFlush(llmRequest);

		JsonNode rawPayload = objectMapper.createObjectNode()
			.put("external_submission_id", "n8n-run-2026-03-15-001")
			.put("source", "telegram");

		FinanceSubmissionEntity submission = new FinanceSubmissionEntity();
		submission.setExternalSubmissionId("n8n-run-2026-03-15-001");
		submission.setRequestId(llmRequest.getRequestId());
		submission.setLlmCollectionRequest(llmRequest);
		submission.setFamily(family);
		submission.setMember(member);
		submission.setSource(SubmissionSource.TELEGRAM);
		submission.setPeriodYear(2026);
		submission.setPeriodMonth((short) 3);
		submission.setCollectedAt(atMoscow("2026-03-17T08:40:00"));
		submission.setMonthlyIncome(120000);
		submission.setMonthlyExpenses(50000);
		submission.setMonthlyCreditPayments(18000);
		submission.setLiquidSavings(150000);
		submission.setConfidence(SubmissionConfidence.MEDIUM);
		submission.setNotes("User provided approximate values");
		submission.setRawPayload(rawPayload);
		financeSubmissionRepository.saveAndFlush(submission);

		entityManager.clear();

		LlmCollectionRequestEntity storedRequest = llmCollectionRequestRepository.findById(llmRequest.getId()).orElseThrow();
		FinanceSubmissionEntity storedSubmission = financeSubmissionRepository.findById(submission.getId()).orElseThrow();

		assertThat(storedRequest.getRequestedFields()).isEqualTo(requestedFields);
		assertThat(storedRequest.getRequestPayload()).isEqualTo(requestPayload);
		assertThat(storedRequest.getResponsePayload()).isEqualTo(responsePayload);
		assertThat(storedRequest.getStatus()).isEqualTo(LlmCollectionRequestStatus.ACCEPTED);
		assertThat(storedRequest.getReason()).isEqualTo(LlmCollectionRequestReason.DAY_AFTER_SALARY);
		assertThat(storedRequest.getCreatedAt()).isNotNull();
		assertThat(storedRequest.getUpdatedAt()).isNotNull();

		assertThat(storedSubmission.getRawPayload()).isEqualTo(rawPayload);
		assertThat(storedSubmission.getSource()).isEqualTo(SubmissionSource.TELEGRAM);
		assertThat(storedSubmission.getConfidence()).isEqualTo(SubmissionConfidence.MEDIUM);
		assertThat(storedSubmission.getCreatedAt()).isNotNull();

		assertThat(jdbcTemplate.queryForObject(
			"select reason from llm_collection_requests where id = ?",
			String.class,
			llmRequest.getId()
		)).isEqualTo("day_after_salary");
		assertThat(jdbcTemplate.queryForObject(
			"select status from llm_collection_requests where id = ?",
			String.class,
			llmRequest.getId()
		)).isEqualTo("accepted");
		assertThat(jdbcTemplate.queryForObject(
			"select source from finance_submissions where id = ?",
			String.class,
			submission.getId()
		)).isEqualTo("telegram");
		assertThat(jdbcTemplate.queryForObject(
			"select confidence from finance_submissions where id = ?",
			String.class,
			submission.getId()
		)).isEqualTo("medium");
	}

	@Test
	void persistsSnapshotEntitiesWithRelationsAndDecimals() {
		FamilyEntity family = saveFamily();
		FamilyMemberEntity member = saveMember(family);
		MemberPayrollScheduleEntity payrollSchedule = savePayrollSchedule(member);
		LlmCollectionRequestEntity request = saveLlmRequest(family, member, payrollSchedule);
		FinanceSubmissionEntity submission = saveFinanceSubmission(family, member, request);

		MemberFinanceSnapshotEntity memberSnapshot = new MemberFinanceSnapshotEntity();
		memberSnapshot.setFamily(family);
		memberSnapshot.setMember(member);
		memberSnapshot.setSourceSubmission(submission);
		memberSnapshot.setPeriodYear(2026);
		memberSnapshot.setPeriodMonth((short) 3);
		memberSnapshot.setMonthlyIncome(120000);
		memberSnapshot.setMonthlyExpenses(50000);
		memberSnapshot.setMonthlyCreditPayments(18000);
		memberSnapshot.setLiquidSavings(150000);
		memberSnapshot.setCollectedAt(atMoscow("2026-03-17T08:40:00"));
		memberFinanceSnapshotRepository.saveAndFlush(memberSnapshot);

		FamilyDashboardSnapshotEntity dashboardSnapshot = new FamilyDashboardSnapshotEntity();
		dashboardSnapshot.setFamily(family);
		dashboardSnapshot.setPeriodYear(2026);
		dashboardSnapshot.setPeriodMonth((short) 3);
		dashboardSnapshot.setStatus(DashboardStatus.WARNING);
		dashboardSnapshot.setStatusText("Внимание");
		dashboardSnapshot.setStatusReason("Подушка ниже комфортной зоны");
		dashboardSnapshot.setMonthlyIncome(210000);
		dashboardSnapshot.setMonthlyExpenses(90000);
		dashboardSnapshot.setCreditLoadPercent(new BigDecimal("27.00"));
		dashboardSnapshot.setEmergencyFundMonths(new BigDecimal("4.20"));
		dashboardSnapshot.setMemberCountUsed(2);
		dashboardSnapshot.setCalculatedAt(atMoscow("2026-03-17T09:10:00"));
		familyDashboardSnapshotRepository.saveAndFlush(dashboardSnapshot);

		entityManager.clear();

		MemberFinanceSnapshotEntity storedMemberSnapshot = memberFinanceSnapshotRepository.findById(memberSnapshot.getId()).orElseThrow();
		FamilyDashboardSnapshotEntity storedDashboardSnapshot = familyDashboardSnapshotRepository.findById(dashboardSnapshot.getId()).orElseThrow();

		assertThat(storedMemberSnapshot.getCreatedAt()).isNotNull();
		assertThat(storedMemberSnapshot.getUpdatedAt()).isNotNull();
		assertThat(storedMemberSnapshot.getSourceSubmission().getId()).isEqualTo(submission.getId());
		assertThat(storedMemberSnapshot.getMonthlyIncome()).isEqualTo(120000);

		assertThat(storedDashboardSnapshot.getCreatedAt()).isNotNull();
		assertThat(storedDashboardSnapshot.getUpdatedAt()).isNotNull();
		assertThat(storedDashboardSnapshot.getStatus()).isEqualTo(DashboardStatus.WARNING);
		assertThat(storedDashboardSnapshot.getCreditLoadPercent()).isEqualByComparingTo("27.00");
		assertThat(storedDashboardSnapshot.getEmergencyFundMonths()).isEqualByComparingTo("4.20");

		assertThat(jdbcTemplate.queryForObject(
			"select status from family_dashboard_snapshots where id = ?",
			String.class,
			dashboardSnapshot.getId()
		)).isEqualTo("warning");
	}

	@Test
	@DisplayName("Возвращает snapshot за последний расчетный период, даже если более старый период пересчитан позже")
	void findsLatestDashboardSnapshotByPeriod() {
		FamilyEntity family = saveFamily();

		FamilyDashboardSnapshotEntity marchSnapshot = new FamilyDashboardSnapshotEntity();
		marchSnapshot.setFamily(family);
		marchSnapshot.setPeriodYear(2026);
		marchSnapshot.setPeriodMonth((short) 3);
		marchSnapshot.setStatus(DashboardStatus.NORMAL);
		marchSnapshot.setStatusText("Норма");
		marchSnapshot.setStatusReason("Показатели в пределах нормы");
		marchSnapshot.setMonthlyIncome(210000);
		marchSnapshot.setMonthlyExpenses(90000);
		marchSnapshot.setCreditLoadPercent(new BigDecimal("27.00"));
		marchSnapshot.setEmergencyFundMonths(new BigDecimal("3.20"));
		marchSnapshot.setMemberCountUsed(2);
		marchSnapshot.setCalculatedAt(atMoscow("2026-03-20T09:00:00"));
		familyDashboardSnapshotRepository.saveAndFlush(marchSnapshot);

		FamilyDashboardSnapshotEntity februarySnapshot = new FamilyDashboardSnapshotEntity();
		februarySnapshot.setFamily(family);
		februarySnapshot.setPeriodYear(2026);
		februarySnapshot.setPeriodMonth((short) 2);
		februarySnapshot.setStatus(DashboardStatus.WARNING);
		februarySnapshot.setStatusText("Внимание");
		februarySnapshot.setStatusReason("Подушка ниже комфортной зоны");
		februarySnapshot.setMonthlyIncome(180000);
		februarySnapshot.setMonthlyExpenses(95000);
		februarySnapshot.setCreditLoadPercent(new BigDecimal("31.00"));
		februarySnapshot.setEmergencyFundMonths(new BigDecimal("2.30"));
		februarySnapshot.setMemberCountUsed(2);
		februarySnapshot.setCalculatedAt(atMoscow("2026-03-21T09:00:00"));
		familyDashboardSnapshotRepository.saveAndFlush(februarySnapshot);

		entityManager.clear();

		FamilyDashboardSnapshotEntity actualSnapshot = familyDashboardSnapshotRepository
			.findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(family.getId())
			.orElseThrow();

		assertThat(actualSnapshot.getPeriodYear()).isEqualTo(2026);
		assertThat(actualSnapshot.getPeriodMonth()).isEqualTo((short) 3);
		assertThat(actualSnapshot.getCalculatedAt()).isEqualTo(atMoscow("2026-03-20T09:00:00"));
	}

	private FamilyEntity saveFamily() {
		FamilyEntity family = new FamilyEntity();
		family.setName("Ivanov family");
		family.setTimezone("Europe/Moscow");
		family.setCurrencyCode("RUB");
		family.setStatus(FamilyStatus.ACTIVE);
		return familyRepository.saveAndFlush(family);
	}

	private FamilyMemberEntity saveMember(FamilyEntity family) {
		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setFamily(family);
		member.setFirstName("Anna");
		member.setLastName("Ivanova");
		member.setDisplayName("Anna");
		member.setTelegramChatId("123456789");
		member.setTelegramUsername("anna_ivanova");
		member.setActive(true);
		return familyMemberRepository.saveAndFlush(member);
	}

	private MemberPayrollScheduleEntity savePayrollSchedule(FamilyMemberEntity member) {
		MemberPayrollScheduleEntity payrollSchedule = new MemberPayrollScheduleEntity();
		payrollSchedule.setMember(member);
		payrollSchedule.setLabel("Main salary");
		payrollSchedule.setScheduleType(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		payrollSchedule.setDayOfMonth((short) 16);
		payrollSchedule.setTriggerDelayDays((short) 1);
		payrollSchedule.setActive(true);
		return memberPayrollScheduleRepository.saveAndFlush(payrollSchedule);
	}

	private LlmCollectionRequestEntity saveLlmRequest(
		FamilyEntity family,
		FamilyMemberEntity member,
		MemberPayrollScheduleEntity payrollSchedule
	) {
		LlmCollectionRequestEntity request = new LlmCollectionRequestEntity();
		request.setRequestId("req-" + member.getId());
		request.setFamily(family);
		request.setMember(member);
		request.setPayrollSchedule(payrollSchedule);
		request.setPeriodYear(2026);
		request.setPeriodMonth((short) 3);
		request.setReason(LlmCollectionRequestReason.DAY_AFTER_SALARY);
		request.setStatus(LlmCollectionRequestStatus.PENDING);
		request.setRequestedFields(objectMapper.createArrayNode().add("monthly_income"));
		request.setNominalPayrollDate(LocalDate.of(2026, 3, 16));
		request.setEffectivePayrollDate(LocalDate.of(2026, 3, 16));
		request.setScheduledTriggerDate(LocalDate.of(2026, 3, 17));
		request.setTriggeredAt(atMoscow("2026-03-17T09:00:00"));
		request.setRequestPayload(objectMapper.createObjectNode().put("request_id", "req-" + member.getId()));
		return llmCollectionRequestRepository.saveAndFlush(request);
	}

	private FinanceSubmissionEntity saveFinanceSubmission(
		FamilyEntity family,
		FamilyMemberEntity member,
		LlmCollectionRequestEntity request
	) {
		FinanceSubmissionEntity submission = new FinanceSubmissionEntity();
		submission.setExternalSubmissionId("sub-" + member.getId());
		submission.setRequestId(request.getRequestId());
		submission.setLlmCollectionRequest(request);
		submission.setFamily(family);
		submission.setMember(member);
		submission.setSource(SubmissionSource.TELEGRAM);
		submission.setPeriodYear(2026);
		submission.setPeriodMonth((short) 3);
		submission.setCollectedAt(atMoscow("2026-03-17T08:40:00"));
		submission.setMonthlyIncome(120000);
		submission.setMonthlyExpenses(50000);
		submission.setMonthlyCreditPayments(18000);
		submission.setLiquidSavings(150000);
		submission.setConfidence(SubmissionConfidence.MEDIUM);
		submission.setNotes("notes");
		submission.setRawPayload(objectMapper.createObjectNode().put("external_submission_id", "sub-" + member.getId()));
		return financeSubmissionRepository.saveAndFlush(submission);
	}

	private OffsetDateTime atMoscow(String localDateTime) {
		return OffsetDateTime.parse(localDateTime + "+03:00");
	}
}

