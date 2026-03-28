package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestReason;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.family.enums.FamilyStatus;
import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import com.deknd.familyfinancemetre.core.payroll.service.PayrollEventCalculationResult;
import com.deknd.familyfinancemetre.shared.config.ApplicationProperties;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.net.URI;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LlmCollectionRequestCreationServiceTest {

	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID PAYROLL_SCHEDULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID REQUEST_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final Clock FIXED_CLOCK = Clock.fixed(
		Instant.parse("2026-03-17T06:00:00Z"),
		ZoneId.of("Europe/Moscow")
	);

	@Mock
	private LlmCollectionRequestRepository llmCollectionRequestRepository;

	private LlmCollectionRequestCreationService llmCollectionRequestCreationService;

	@BeforeEach
	void setUp() {
		llmCollectionRequestCreationService = new LlmCollectionRequestCreationService(
			llmCollectionRequestRepository,
			applicationProperties(),
			FIXED_CLOCK
		);
	}

	@Test
	@DisplayName("Создает pending запрос с payroll-данными, request_payload и UUID correlation id")
	void createPendingRequestPersistsNewRequestWithPayload() {
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule();
		PayrollEventCalculationResult payrollEvent = payrollEvent();

		given(llmCollectionRequestRepository.findByPayrollScheduleIdAndEffectivePayrollDate(
			PAYROLL_SCHEDULE_ID,
			LocalDate.of(2026, 3, 16)
		)).willReturn(Optional.empty());
		given(llmCollectionRequestRepository.saveAndFlush(any(LlmCollectionRequestEntity.class)))
			.willAnswer(invocation -> {
				LlmCollectionRequestEntity savedRequest = invocation.getArgument(0);
				savedRequest.setId(REQUEST_ID);
				return savedRequest;
			});

		Optional<LlmCollectionRequestEntity> createdRequest = llmCollectionRequestCreationService.createPendingRequest(
			payrollSchedule,
			payrollEvent
		);

		ArgumentCaptor<LlmCollectionRequestEntity> requestCaptor = ArgumentCaptor.forClass(LlmCollectionRequestEntity.class);
		verify(llmCollectionRequestRepository).saveAndFlush(requestCaptor.capture());

		LlmCollectionRequestEntity savedRequest = requestCaptor.getValue();
		assertThat(createdRequest).containsSame(savedRequest);
		assertThat(savedRequest.getId()).isEqualTo(REQUEST_ID);
		assertThat(savedRequest.getFamily().getId()).isEqualTo(FAMILY_ID);
		assertThat(savedRequest.getMember().getId()).isEqualTo(MEMBER_ID);
		assertThat(savedRequest.getPayrollSchedule().getId()).isEqualTo(PAYROLL_SCHEDULE_ID);
		assertThat(savedRequest.getPeriodYear()).isEqualTo(2026);
		assertThat(savedRequest.getPeriodMonth()).isEqualTo((short) 3);
		assertThat(savedRequest.getReason()).isEqualTo(LlmCollectionRequestReason.DAY_AFTER_SALARY);
		assertThat(savedRequest.getStatus()).isEqualTo(LlmCollectionRequestStatus.PENDING);
		assertThat(savedRequest.getNominalPayrollDate()).isEqualTo(LocalDate.of(2026, 3, 16));
		assertThat(savedRequest.getEffectivePayrollDate()).isEqualTo(LocalDate.of(2026, 3, 16));
		assertThat(savedRequest.getScheduledTriggerDate()).isEqualTo(LocalDate.of(2026, 3, 17));
		assertThat(savedRequest.getTriggeredAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
		assertThat(savedRequest.getRequestedFields().get(0).asText()).isEqualTo("monthly_income");
		assertThat(savedRequest.getRequestedFields().get(1).asText()).isEqualTo("monthly_expenses");
		assertThat(savedRequest.getRequestedFields().get(2).asText()).isEqualTo("monthly_credit_payments");
		assertThat(savedRequest.getRequestedFields().get(3).asText()).isEqualTo("liquid_savings");
		assertThat(savedRequest.getRequestId()).matches(
			"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
		);
		assertThat(savedRequest.getRequestPayload().get("request_id").asText()).isEqualTo(savedRequest.getRequestId());
		assertThat(savedRequest.getRequestPayload().get("triggered_at").asText())
			.isEqualTo(OffsetDateTime.now(FIXED_CLOCK).toString());
		assertThat(savedRequest.getRequestPayload().get("reason").asText()).isEqualTo("day_after_salary");
		assertThat(savedRequest.getRequestPayload().at("/family/id").asText()).isEqualTo(FAMILY_ID.toString());
		assertThat(savedRequest.getRequestPayload().at("/family/name").asText()).isEqualTo("Ivanov family");
		assertThat(savedRequest.getRequestPayload().at("/member/id").asText()).isEqualTo(MEMBER_ID.toString());
		assertThat(savedRequest.getRequestPayload().at("/member/name").asText()).isEqualTo("Anna");
		assertThat(savedRequest.getRequestPayload().at("/member/telegram_chat_id").asText()).isEqualTo("123456789");
		assertThat(savedRequest.getRequestPayload().at("/payroll_event/schedule_id").asText()).isEqualTo(PAYROLL_SCHEDULE_ID.toString());
		assertThat(savedRequest.getRequestPayload().at("/payroll_event/schedule_type").asText()).isEqualTo("fixed_day_of_month");
		assertThat(savedRequest.getRequestPayload().at("/payroll_event/day_of_month").asInt()).isEqualTo(16);
		assertThat(savedRequest.getRequestPayload().at("/payroll_event/nominal_payroll_date").asText()).isEqualTo("2026-03-16");
		assertThat(savedRequest.getRequestPayload().at("/payroll_event/effective_payroll_date").asText()).isEqualTo("2026-03-16");
		assertThat(savedRequest.getRequestPayload().at("/payroll_event/trigger_delay_days").asInt()).isEqualTo(1);
		assertThat(savedRequest.getRequestPayload().at("/payroll_event/scheduled_trigger_date").asText()).isEqualTo("2026-03-17");
		assertThat(savedRequest.getRequestPayload().at("/collection_scope/period_year").asInt()).isEqualTo(2026);
		assertThat(savedRequest.getRequestPayload().at("/collection_scope/period_month").asInt()).isEqualTo(3);
		assertThat(savedRequest.getRequestPayload().at("/collection_scope/fields/0").asText()).isEqualTo("monthly_income");
		assertThat(savedRequest.getRequestPayload().at("/instructions/locale").asText()).isEqualTo("ru-RU");
		assertThat(savedRequest.getRequestPayload().at("/instructions/currency").asText()).isEqualTo("RUB");
		assertThat(savedRequest.getRequestPayload().at("/instructions/allow_approximate_values").asBoolean()).isTrue();
		assertThat(savedRequest.getRequestPayload().at("/instructions/max_clarifying_questions_per_field").asInt()).isEqualTo(1);
		assertThat(savedRequest.getRequestPayload().at("/callback/submit_url").asText())
			.isEqualTo("https://server.example.com/api/v1/intake/user-finance-data");
	}

	@Test
	@DisplayName("Возвращает пустой результат и не сохраняет запись, если payroll-событие уже существует")
	void createPendingRequestReturnsEmptyWhenPayrollEventAlreadyExists() {
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule();
		PayrollEventCalculationResult payrollEvent = payrollEvent();

		given(llmCollectionRequestRepository.findByPayrollScheduleIdAndEffectivePayrollDate(
			PAYROLL_SCHEDULE_ID,
			LocalDate.of(2026, 3, 16)
		)).willReturn(Optional.of(new LlmCollectionRequestEntity()));

		Optional<LlmCollectionRequestEntity> createdRequest = llmCollectionRequestCreationService.createPendingRequest(
			payrollSchedule,
			payrollEvent
		);

		assertThat(createdRequest).isEmpty();
		verify(llmCollectionRequestRepository, never()).saveAndFlush(any(LlmCollectionRequestEntity.class));
	}

	@Test
	@DisplayName("Возвращает пустой результат, если дубликат выявлен уникальным индексом во время сохранения")
	void createPendingRequestReturnsEmptyWhenUniqueConstraintIsViolatedDuringSave() {
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule();
		PayrollEventCalculationResult payrollEvent = payrollEvent();

		given(llmCollectionRequestRepository.findByPayrollScheduleIdAndEffectivePayrollDate(
			PAYROLL_SCHEDULE_ID,
			LocalDate.of(2026, 3, 16)
		)).willReturn(Optional.empty());
		given(llmCollectionRequestRepository.saveAndFlush(any(LlmCollectionRequestEntity.class)))
			.willThrow(new DataIntegrityViolationException(
				"duplicate payroll event",
				new ConstraintViolationException(
					"duplicate key value violates unique constraint",
					new SQLException("duplicate"),
					"insert into llm_collection_requests ...",
					"uq_llm_requests_schedule_effective_date"
				)
			));

		Optional<LlmCollectionRequestEntity> createdRequest = llmCollectionRequestCreationService.createPendingRequest(
			payrollSchedule,
			payrollEvent
		);

		assertThat(createdRequest).isEmpty();
	}

	@Test
	@DisplayName("Пробрасывает посторонние DataIntegrityViolationException без маскировки")
	void createPendingRequestPropagatesNonDuplicatePersistenceFailure() {
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule();
		PayrollEventCalculationResult payrollEvent = payrollEvent();
		DataIntegrityViolationException exception = new DataIntegrityViolationException("foreign key violation");

		given(llmCollectionRequestRepository.findByPayrollScheduleIdAndEffectivePayrollDate(
			PAYROLL_SCHEDULE_ID,
			LocalDate.of(2026, 3, 16)
		)).willReturn(Optional.empty());
		given(llmCollectionRequestRepository.saveAndFlush(any(LlmCollectionRequestEntity.class))).willThrow(exception);

		assertThatThrownBy(() -> llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, payrollEvent))
			.isSameAs(exception);
	}

	private static ApplicationProperties applicationProperties() {
		return new ApplicationProperties(
			ZoneId.of("Europe/Moscow"),
			new ApplicationProperties.Security("migration-n8n-api-key"),
			new ApplicationProperties.Integrations(
				new ApplicationProperties.N8n(
					URI.create("https://n8n.example.com/webhook/finance-intake-start"),
					"bearer-token",
					URI.create("https://server.example.com/api/v1/intake/user-finance-data"),
					java.time.Duration.ofSeconds(5),
					java.time.Duration.ofSeconds(30)
				)
			),
			new ApplicationProperties.Scheduler(
				new ApplicationProperties.PayrollCollection(true, "0 0 9 * * *")
			)
		);
	}

	private MemberPayrollScheduleEntity payrollSchedule() {
		FamilyEntity family = new FamilyEntity();
		family.setId(FAMILY_ID);
		family.setName("Ivanov family");
		family.setTimezone("Europe/Moscow");
		family.setCurrencyCode("RUB");
		family.setStatus(FamilyStatus.ACTIVE);

		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setId(MEMBER_ID);
		member.setFamily(family);
		member.setFirstName("Anna");
		member.setDisplayName("Anna");
		member.setTelegramChatId("123456789");
		member.setActive(true);

		MemberPayrollScheduleEntity payrollSchedule = new MemberPayrollScheduleEntity();
		payrollSchedule.setId(PAYROLL_SCHEDULE_ID);
		payrollSchedule.setMember(member);
		payrollSchedule.setScheduleType(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		payrollSchedule.setDayOfMonth((short) 16);
		payrollSchedule.setTriggerDelayDays((short) 1);
		payrollSchedule.setActive(true);
		return payrollSchedule;
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
