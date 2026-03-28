package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.entity.enums.PayrollScheduleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedDayOfMonthPayrollEventCalculationStrategyTest {

	private final FixedDayOfMonthPayrollEventCalculationStrategy strategy =
		new FixedDayOfMonthPayrollEventCalculationStrategy();

	@Test
	@DisplayName("Возвращает payroll-событие для фиксированного дня месяца без переноса выходного")
	void calculateReturnsPayrollEventForFixedDayWithoutWeekendShift() {
		MemberPayrollScheduleEntity schedule = schedule((short) 16, (short) 1);

		PayrollEventCalculationResult result = strategy.calculate(schedule, YearMonth.of(2026, 3));

		assertThat(result.nominalPayrollDate()).isEqualTo(LocalDate.of(2026, 3, 16));
		assertThat(result.effectivePayrollDate()).isEqualTo(LocalDate.of(2026, 3, 16));
		assertThat(result.scheduledTriggerDate()).isEqualTo(LocalDate.of(2026, 3, 17));
		assertThat(result.periodYear()).isEqualTo(2026);
		assertThat(result.periodMonth()).isEqualTo((short) 3);
	}

	@Test
	@DisplayName("Переносит выплату с субботы на предыдущую пятницу")
	void calculateMovesSaturdayPayrollToPreviousFriday() {
		MemberPayrollScheduleEntity schedule = schedule((short) 18, (short) 1);

		PayrollEventCalculationResult result = strategy.calculate(schedule, YearMonth.of(2026, 7));

		assertThat(result.nominalPayrollDate()).isEqualTo(LocalDate.of(2026, 7, 18));
		assertThat(result.effectivePayrollDate()).isEqualTo(LocalDate.of(2026, 7, 17));
		assertThat(result.scheduledTriggerDate()).isEqualTo(LocalDate.of(2026, 7, 18));
		assertThat(result.periodYear()).isEqualTo(2026);
		assertThat(result.periodMonth()).isEqualTo((short) 7);
	}

	@Test
	@DisplayName("Переносит выплату с воскресенья на предыдущую пятницу")
	void calculateMovesSundayPayrollToPreviousFriday() {
		MemberPayrollScheduleEntity schedule = schedule((short) 31, (short) 1);

		PayrollEventCalculationResult result = strategy.calculate(schedule, YearMonth.of(2026, 5));

		assertThat(result.nominalPayrollDate()).isEqualTo(LocalDate.of(2026, 5, 31));
		assertThat(result.effectivePayrollDate()).isEqualTo(LocalDate.of(2026, 5, 29));
		assertThat(result.scheduledTriggerDate()).isEqualTo(LocalDate.of(2026, 5, 30));
		assertThat(result.periodYear()).isEqualTo(2026);
		assertThat(result.periodMonth()).isEqualTo((short) 5);
	}

	@Test
	@DisplayName("Обрезает фиксированный день до конца короткого месяца перед применением переноса")
	void calculateClampsFixedDayToEndOfShortMonthBeforeWeekendShift() {
		MemberPayrollScheduleEntity schedule = schedule((short) 31, (short) 1);

		PayrollEventCalculationResult result = strategy.calculate(schedule, YearMonth.of(2026, 4));

		assertThat(result.nominalPayrollDate()).isEqualTo(LocalDate.of(2026, 4, 30));
		assertThat(result.effectivePayrollDate()).isEqualTo(LocalDate.of(2026, 4, 30));
		assertThat(result.scheduledTriggerDate()).isEqualTo(LocalDate.of(2026, 5, 1));
		assertThat(result.periodYear()).isEqualTo(2026);
		assertThat(result.periodMonth()).isEqualTo((short) 4);
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если у fixed day правила не указан день месяца")
	void calculateThrowsWhenFixedDayScheduleDoesNotContainDayOfMonth() {
		MemberPayrollScheduleEntity schedule = schedule(null, (short) 1);

		assertThatThrownBy(() -> strategy.calculate(schedule, YearMonth.of(2026, 3)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Payroll schedule dayOfMonth must not be null for fixed day schedule");
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если день месяца выходит за допустимый диапазон")
	void calculateThrowsWhenDayOfMonthIsOutsideSupportedRange() {
		MemberPayrollScheduleEntity schedule = schedule((short) 32, (short) 1);

		assertThatThrownBy(() -> strategy.calculate(schedule, YearMonth.of(2026, 3)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Payroll schedule dayOfMonth must be between 1 and 31 for fixed day schedule");
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если у правила не указан trigger delay")
	void calculateThrowsWhenTriggerDelayDaysIsMissing() {
		MemberPayrollScheduleEntity schedule = schedule((short) 16, null);

		assertThatThrownBy(() -> strategy.calculate(schedule, YearMonth.of(2026, 3)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Payroll schedule triggerDelayDays must not be null");
	}

	private MemberPayrollScheduleEntity schedule(Short dayOfMonth, Short triggerDelayDays) {
		MemberPayrollScheduleEntity schedule = new MemberPayrollScheduleEntity();
		schedule.setScheduleType(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		schedule.setDayOfMonth(dayOfMonth);
		schedule.setTriggerDelayDays(triggerDelayDays);
		return schedule;
	}
}
