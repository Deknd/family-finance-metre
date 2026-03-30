package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.family.enums.FamilyStatus;
import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.repository.MemberPayrollScheduleRepository;
import com.deknd.familyfinancemetre.core.payroll.service.PayrollEventCalculationResult;
import com.deknd.familyfinancemetre.core.payroll.service.PayrollEventCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Оркестрирует ежедневный поиск payroll-событий, для которых сегодня нужно запустить сбор данных.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PayrollCollectionOrchestrationService {

	private final MemberPayrollScheduleRepository memberPayrollScheduleRepository;
	private final PayrollEventCalculationService payrollEventCalculationService;
	private final LlmCollectionRequestCreationService llmCollectionRequestCreationService;
	private final LlmCollectionRequestDispatchService llmCollectionRequestDispatchService;
	private final Clock clock;

	/**
	 * Запускает ежедневную обработку payroll-правил и инициирует сбор данных
	 * для событий, дата запуска которых совпадает с текущей датой семьи.
	 */
	public void runDailyPayrollCollection() {
		List<MemberPayrollScheduleEntity> payrollSchedules =
			memberPayrollScheduleRepository.findAllByActiveTrueAndMemberActiveTrueAndMemberFamilyStatus(FamilyStatus.ACTIVE);

		for (MemberPayrollScheduleEntity payrollSchedule : payrollSchedules) {
			try {
				processPayrollSchedule(payrollSchedule);
			} catch (RuntimeException exception) {
				log.error(
					"Не удалось обработать payroll-правило id={} для memberId={} familyId={}",
					resolveScheduleId(payrollSchedule),
					resolveMemberId(payrollSchedule),
					resolveFamilyId(payrollSchedule),
					exception
				);
			}
		}
	}

	private void processPayrollSchedule(MemberPayrollScheduleEntity payrollSchedule) {
		LocalDate familyToday = resolveFamilyToday(payrollSchedule);
		for (YearMonth targetMonth : resolveCandidateMonths(familyToday)) {
			PayrollEventCalculationResult payrollEvent = payrollEventCalculationService.calculate(payrollSchedule, targetMonth);
			if (!payrollEvent.scheduledTriggerDate().equals(familyToday)) {
				continue;
			}

			Optional<LlmCollectionRequestEntity> createdRequest =
				llmCollectionRequestCreationService.createPendingRequest(payrollSchedule, payrollEvent);
			createdRequest.ifPresent(llmCollectionRequestDispatchService::dispatchPendingRequest);
			return;
		}
	}

	private List<YearMonth> resolveCandidateMonths(LocalDate familyToday) {
		YearMonth currentMonth = YearMonth.from(familyToday);
		return List.of(currentMonth, currentMonth.minusMonths(1));
	}

	private LocalDate resolveFamilyToday(MemberPayrollScheduleEntity payrollSchedule) {
		FamilyEntity family = requireFamily(payrollSchedule);
		ZoneId familyZoneId = ZoneId.of(Objects.requireNonNull(family.getTimezone(), "Family timezone must not be null"));
		return LocalDate.now(clock.withZone(familyZoneId));
	}

	private FamilyEntity requireFamily(MemberPayrollScheduleEntity payrollSchedule) {
		FamilyMemberEntity member = Objects.requireNonNull(payrollSchedule.getMember(), "Payroll schedule member must not be null");
		return Objects.requireNonNull(member.getFamily(), "Payroll schedule family must not be null");
	}

	private UUID resolveScheduleId(MemberPayrollScheduleEntity payrollSchedule) {
		return payrollSchedule.getId();
	}

	private UUID resolveMemberId(MemberPayrollScheduleEntity payrollSchedule) {
		FamilyMemberEntity member = payrollSchedule.getMember();
		return member == null ? null : member.getId();
	}

	private UUID resolveFamilyId(MemberPayrollScheduleEntity payrollSchedule) {
		FamilyMemberEntity member = payrollSchedule.getMember();
		FamilyEntity family = member == null ? null : member.getFamily();
		return family == null ? null : family.getId();
	}
}
