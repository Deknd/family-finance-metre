package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.entity.enums.PayrollScheduleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LastDayOfMonthPayrollEventCalculationStrategyTest {

	private final LastDayOfMonthPayrollEventCalculationStrategy strategy =
		new LastDayOfMonthPayrollEventCalculationStrategy();

	@Test
	@DisplayName("Использует последний календарный день месяца для правила last day")
	void calculateUsesLastCalendarDayForLastDaySchedule() {
		MemberPayrollScheduleEntity schedule = schedule(null, (short) 1);

		PayrollEventCalculationResult result = strategy.calculate(schedule, YearMonth.of(2026, 2));

		assertThat(result.nominalPayrollDate()).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(result.effectivePayrollDate()).isEqualTo(LocalDate.of(2026, 2, 27));
		assertThat(result.scheduledTriggerDate()).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(result.periodYear()).isEqualTo(2026);
		assertThat(result.periodMonth()).isEqualTo((short) 2);
	}

	@Test
	@DisplayName("Сохраняет период по фактической дате выплаты, даже если запуск опроса попадает в следующий месяц")
	void calculateKeepsPeriodBasedOnEffectivePayrollDateWhenTriggerMovesToNextMonth() {
		MemberPayrollScheduleEntity schedule = schedule(null, (short) 1);

		PayrollEventCalculationResult result = strategy.calculate(schedule, YearMonth.of(2026, 3));

		assertThat(result.nominalPayrollDate()).isEqualTo(LocalDate.of(2026, 3, 31));
		assertThat(result.effectivePayrollDate()).isEqualTo(LocalDate.of(2026, 3, 31));
		assertThat(result.scheduledTriggerDate()).isEqualTo(LocalDate.of(2026, 4, 1));
		assertThat(result.periodYear()).isEqualTo(2026);
		assertThat(result.periodMonth()).isEqualTo((short) 3);
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если у last day правила заполнен day of month")
	void calculateThrowsWhenLastDayScheduleContainsDayOfMonth() {
		MemberPayrollScheduleEntity schedule = schedule((short) 31, (short) 1);

		assertThatThrownBy(() -> strategy.calculate(schedule, YearMonth.of(2026, 3)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Payroll schedule dayOfMonth must be null for last day schedule");
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если у правила не указан trigger delay")
	void calculateThrowsWhenTriggerDelayDaysIsMissing() {
		MemberPayrollScheduleEntity schedule = schedule(null, null);

		assertThatThrownBy(() -> strategy.calculate(schedule, YearMonth.of(2026, 3)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Payroll schedule triggerDelayDays must not be null");
	}

	private MemberPayrollScheduleEntity schedule(Short dayOfMonth, Short triggerDelayDays) {
		MemberPayrollScheduleEntity schedule = new MemberPayrollScheduleEntity();
		schedule.setScheduleType(PayrollScheduleType.LAST_DAY_OF_MONTH);
		schedule.setDayOfMonth(dayOfMonth);
		schedule.setTriggerDelayDays(triggerDelayDays);
		return schedule;
	}
}
