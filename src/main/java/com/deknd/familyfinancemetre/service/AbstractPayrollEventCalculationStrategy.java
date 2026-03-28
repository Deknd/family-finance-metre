package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.MemberPayrollScheduleEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

abstract class AbstractPayrollEventCalculationStrategy implements PayrollEventCalculationStrategy {

	/**
	 * Выполняет общий расчет payroll-события и делегирует специфичную часть конкретной стратегии.
	 *
	 * @param schedule зарплатное правило участника семьи
	 * @param targetMonth целевой месяц расчета
	 * @return набор рассчитанных дат payroll-события и его расчетного периода
	 */
	@Override
	public final PayrollEventCalculationResult calculate(MemberPayrollScheduleEntity schedule, YearMonth targetMonth) {
		Objects.requireNonNull(schedule, "Payroll schedule must not be null");
		Objects.requireNonNull(targetMonth, "Target month must not be null");

		if (schedule.getTriggerDelayDays() == null) {
			throw invalidSchedule("Payroll schedule triggerDelayDays must not be null");
		}

		validateSchedule(schedule);

		LocalDate nominalPayrollDate = calculateNominalPayrollDate(schedule, targetMonth);
		LocalDate effectivePayrollDate = adjustToEffectivePayrollDate(nominalPayrollDate);
		LocalDate scheduledTriggerDate = effectivePayrollDate.plusDays(schedule.getTriggerDelayDays());

		return new PayrollEventCalculationResult(
			nominalPayrollDate,
			effectivePayrollDate,
			scheduledTriggerDate,
			effectivePayrollDate.getYear(),
			(short) effectivePayrollDate.getMonthValue()
		);
	}

	protected abstract void validateSchedule(MemberPayrollScheduleEntity schedule);

	protected abstract LocalDate calculateNominalPayrollDate(MemberPayrollScheduleEntity schedule, YearMonth targetMonth);

	protected final IllegalStateException invalidSchedule(String message) {
		return new IllegalStateException(message);
	}

	private LocalDate adjustToEffectivePayrollDate(LocalDate nominalPayrollDate) {
		DayOfWeek dayOfWeek = nominalPayrollDate.getDayOfWeek();
		if (dayOfWeek == DayOfWeek.SATURDAY) {
			return nominalPayrollDate.minusDays(1);
		}
		if (dayOfWeek == DayOfWeek.SUNDAY) {
			return nominalPayrollDate.minusDays(2);
		}
		return nominalPayrollDate;
	}
}
