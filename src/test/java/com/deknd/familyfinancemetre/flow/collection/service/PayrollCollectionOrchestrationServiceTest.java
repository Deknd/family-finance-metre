package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.family.enums.FamilyStatus;
import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import com.deknd.familyfinancemetre.core.payroll.repository.MemberPayrollScheduleRepository;
import com.deknd.familyfinancemetre.core.payroll.service.PayrollEventCalculationResult;
import com.deknd.familyfinancemetre.core.payroll.service.PayrollEventCalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PayrollCollectionOrchestrationServiceTest {

	private static final ZoneId APPLICATION_ZONE = ZoneId.of("Europe/Moscow");

	@Mock
	private MemberPayrollScheduleRepository memberPayrollScheduleRepository;

	@Mock
	private PayrollEventCalculationService payrollEventCalculationService;

	@Mock
	private LlmCollectionRequestCreationService llmCollectionRequestCreationService;

	@Mock
	private LlmCollectionRequestDispatchService llmCollectionRequestDispatchService;

	@Test
	@DisplayName("Создает запрос и вызывает dispatch, если scheduled trigger совпал с сегодняшней датой семьи")
	void runDailyPayrollCollectionCreatesRequestAndDispatchesWhenScheduleIsDueToday() {
		PayrollCollectionOrchestrationService service = serviceAt("2026-03-17T06:00:00Z");
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule("Europe/Moscow");
		PayrollEventCalculationResult duePayrollEvent = payrollEvent(
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 17),
			2026,
			(short) 3
		);
		LlmCollectionRequestEntity createdRequest = createdRequest();
		given(memberPayrollScheduleRepository.findAllByActiveTrueAndMemberActiveTrueAndMemberFamilyStatus(FamilyStatus.ACTIVE))
			.willReturn(List.of(payrollSchedule));
		given(payrollEventCalculationService.calculate(payrollSchedule, YearMonth.of(2026, 3))).willReturn(duePayrollEvent);
		given(llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, duePayrollEvent))
			.willReturn(Optional.of(createdRequest));

		service.runDailyPayrollCollection();

		verify(payrollEventCalculationService).calculate(payrollSchedule, YearMonth.of(2026, 3));
		verify(llmCollectionRequestCreationService).createPendingRequest(payrollSchedule, duePayrollEvent);
		verify(llmCollectionRequestDispatchService).dispatchPendingRequest(createdRequest);
		verify(payrollEventCalculationService, never()).calculate(payrollSchedule, YearMonth.of(2026, 2));
	}

	@Test
	@DisplayName("Проверяет предыдущий месяц, если запуск опроса попал на первый день следующего месяца")
	void runDailyPayrollCollectionChecksPreviousMonthForCrossMonthTrigger() {
		PayrollCollectionOrchestrationService service = serviceAt("2026-04-01T06:00:00Z");
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule("Europe/Moscow");
		PayrollEventCalculationResult currentMonthEvent = payrollEvent(
			LocalDate.of(2026, 4, 16),
			LocalDate.of(2026, 4, 16),
			LocalDate.of(2026, 4, 17),
			2026,
			(short) 4
		);
		PayrollEventCalculationResult previousMonthEvent = payrollEvent(
			LocalDate.of(2026, 3, 31),
			LocalDate.of(2026, 3, 31),
			LocalDate.of(2026, 4, 1),
			2026,
			(short) 3
		);
		LlmCollectionRequestEntity createdRequest = createdRequest();
		given(memberPayrollScheduleRepository.findAllByActiveTrueAndMemberActiveTrueAndMemberFamilyStatus(FamilyStatus.ACTIVE))
			.willReturn(List.of(payrollSchedule));
		given(payrollEventCalculationService.calculate(payrollSchedule, YearMonth.of(2026, 4))).willReturn(currentMonthEvent);
		given(payrollEventCalculationService.calculate(payrollSchedule, YearMonth.of(2026, 3))).willReturn(previousMonthEvent);
		given(llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, previousMonthEvent))
			.willReturn(Optional.of(createdRequest));

		service.runDailyPayrollCollection();

		verify(payrollEventCalculationService).calculate(payrollSchedule, YearMonth.of(2026, 4));
		verify(payrollEventCalculationService).calculate(payrollSchedule, YearMonth.of(2026, 3));
		verify(llmCollectionRequestCreationService).createPendingRequest(payrollSchedule, previousMonthEvent);
		verify(llmCollectionRequestDispatchService).dispatchPendingRequest(createdRequest);
	}

	@Test
	@DisplayName("Пропускает payroll правило, если ни текущий, ни предыдущий месяц не дают запуск на сегодня")
	void runDailyPayrollCollectionSkipsScheduleWhenNothingIsDueToday() {
		PayrollCollectionOrchestrationService service = serviceAt("2026-03-17T06:00:00Z");
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule("Europe/Moscow");
		given(memberPayrollScheduleRepository.findAllByActiveTrueAndMemberActiveTrueAndMemberFamilyStatus(FamilyStatus.ACTIVE))
			.willReturn(List.of(payrollSchedule));
		given(payrollEventCalculationService.calculate(payrollSchedule, YearMonth.of(2026, 3))).willReturn(payrollEvent(
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 18),
			2026,
			(short) 3
		));
		given(payrollEventCalculationService.calculate(payrollSchedule, YearMonth.of(2026, 2))).willReturn(payrollEvent(
			LocalDate.of(2026, 2, 16),
			LocalDate.of(2026, 2, 16),
			LocalDate.of(2026, 2, 17),
			2026,
			(short) 2
		));

		service.runDailyPayrollCollection();

		verify(payrollEventCalculationService).calculate(payrollSchedule, YearMonth.of(2026, 3));
		verify(payrollEventCalculationService).calculate(payrollSchedule, YearMonth.of(2026, 2));
		verifyNoInteractions(llmCollectionRequestCreationService, llmCollectionRequestDispatchService);
	}

	@Test
	@DisplayName("Не вызывает dispatch повторно, если запрос для payroll события уже был создан ранее")
	void runDailyPayrollCollectionDoesNotDispatchDuplicateRequest() {
		PayrollCollectionOrchestrationService service = serviceAt("2026-03-17T06:00:00Z");
		MemberPayrollScheduleEntity payrollSchedule = payrollSchedule("Europe/Moscow");
		PayrollEventCalculationResult duePayrollEvent = payrollEvent(
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 17),
			2026,
			(short) 3
		);
		given(memberPayrollScheduleRepository.findAllByActiveTrueAndMemberActiveTrueAndMemberFamilyStatus(FamilyStatus.ACTIVE))
			.willReturn(List.of(payrollSchedule));
		given(payrollEventCalculationService.calculate(payrollSchedule, YearMonth.of(2026, 3))).willReturn(duePayrollEvent);
		given(llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, duePayrollEvent))
			.willReturn(Optional.empty());

		service.runDailyPayrollCollection();

		verify(llmCollectionRequestCreationService).createPendingRequest(payrollSchedule, duePayrollEvent);
		verifyNoInteractions(llmCollectionRequestDispatchService);
	}

	@Test
	@DisplayName("Продолжает обработку остальных правил, если одно из payroll правил завершилось ошибкой")
	void runDailyPayrollCollectionContinuesWhenOneScheduleFails() {
		PayrollCollectionOrchestrationService service = serviceAt("2026-03-17T06:00:00Z");
		MemberPayrollScheduleEntity failingSchedule = payrollSchedule("Europe/Moscow");
		failingSchedule.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
		MemberPayrollScheduleEntity successfulSchedule = payrollSchedule("Europe/Moscow");
		successfulSchedule.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
		PayrollEventCalculationResult duePayrollEvent = payrollEvent(
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 17),
			2026,
			(short) 3
		);
		LlmCollectionRequestEntity createdRequest = createdRequest();
		given(memberPayrollScheduleRepository.findAllByActiveTrueAndMemberActiveTrueAndMemberFamilyStatus(FamilyStatus.ACTIVE))
			.willReturn(List.of(failingSchedule, successfulSchedule));
		given(payrollEventCalculationService.calculate(failingSchedule, YearMonth.of(2026, 3)))
			.willThrow(new IllegalStateException("broken schedule"));
		given(payrollEventCalculationService.calculate(successfulSchedule, YearMonth.of(2026, 3))).willReturn(duePayrollEvent);
		given(llmCollectionRequestCreationService.createPendingRequest(successfulSchedule, duePayrollEvent))
			.willReturn(Optional.of(createdRequest));

		service.runDailyPayrollCollection();

		verify(payrollEventCalculationService).calculate(failingSchedule, YearMonth.of(2026, 3));
		verify(payrollEventCalculationService).calculate(successfulSchedule, YearMonth.of(2026, 3));
		verify(llmCollectionRequestCreationService).createPendingRequest(successfulSchedule, duePayrollEvent);
		verify(llmCollectionRequestDispatchService).dispatchPendingRequest(createdRequest);
	}

	private PayrollCollectionOrchestrationService serviceAt(String instant) {
		return new PayrollCollectionOrchestrationService(
			memberPayrollScheduleRepository,
			payrollEventCalculationService,
			llmCollectionRequestCreationService,
			llmCollectionRequestDispatchService,
			Clock.fixed(Instant.parse(instant), APPLICATION_ZONE)
		);
	}

	private MemberPayrollScheduleEntity payrollSchedule(String timezone) {
		FamilyEntity family = new FamilyEntity();
		family.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
		family.setTimezone(timezone);
		family.setStatus(FamilyStatus.ACTIVE);

		FamilyMemberEntity member = new FamilyMemberEntity();
		member.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
		member.setFamily(family);
		member.setActive(true);

		MemberPayrollScheduleEntity payrollSchedule = new MemberPayrollScheduleEntity();
		payrollSchedule.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
		payrollSchedule.setMember(member);
		payrollSchedule.setActive(true);
		payrollSchedule.setScheduleType(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		payrollSchedule.setDayOfMonth((short) 16);
		payrollSchedule.setTriggerDelayDays((short) 1);
		return payrollSchedule;
	}

	private PayrollEventCalculationResult payrollEvent(
		LocalDate nominalPayrollDate,
		LocalDate effectivePayrollDate,
		LocalDate scheduledTriggerDate,
		int periodYear,
		short periodMonth
	) {
		return new PayrollEventCalculationResult(
			nominalPayrollDate,
			effectivePayrollDate,
			scheduledTriggerDate,
			periodYear,
			periodMonth
		);
	}

	private LlmCollectionRequestEntity createdRequest() {
		LlmCollectionRequestEntity request = new LlmCollectionRequestEntity();
		request.setRequestId("99999999-9999-9999-9999-999999999999");
		return request;
	}
}
