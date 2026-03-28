package com.deknd.familyfinancemetre.core.payroll.service;

import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayrollEventCalculationServiceTest {

	@Mock
	private PayrollEventCalculationStrategy fixedDayStrategy;

	@Mock
	private PayrollEventCalculationStrategy lastDayStrategy;

	private PayrollEventCalculationService payrollEventCalculationService;

	@BeforeEach
	void setUp() {
		given(fixedDayStrategy.supportedScheduleType()).willReturn(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		given(lastDayStrategy.supportedScheduleType()).willReturn(PayrollScheduleType.LAST_DAY_OF_MONTH);
		payrollEventCalculationService = new PayrollEventCalculationService(List.of(fixedDayStrategy, lastDayStrategy));
	}

	@Test
	@DisplayName("Делегирует расчет fixed day правила подходящей стратегии")
	void calculateDelegatesFixedDayScheduleToMatchingStrategy() {
		MemberPayrollScheduleEntity schedule = schedule(PayrollScheduleType.FIXED_DAY_OF_MONTH);
		YearMonth targetMonth = YearMonth.of(2026, 3);
		PayrollEventCalculationResult expectedResult = new PayrollEventCalculationResult(
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 16),
			LocalDate.of(2026, 3, 17),
			2026,
			(short) 3
		);
		given(fixedDayStrategy.calculate(schedule, targetMonth)).willReturn(expectedResult);

		PayrollEventCalculationResult result = payrollEventCalculationService.calculate(schedule, targetMonth);

		assertThat(result).isSameAs(expectedResult);
		verify(fixedDayStrategy).calculate(schedule, targetMonth);
	}

	@Test
	@DisplayName("Делегирует расчет last day правила подходящей стратегии")
	void calculateDelegatesLastDayScheduleToMatchingStrategy() {
		MemberPayrollScheduleEntity schedule = schedule(PayrollScheduleType.LAST_DAY_OF_MONTH);
		YearMonth targetMonth = YearMonth.of(2026, 2);
		PayrollEventCalculationResult expectedResult = new PayrollEventCalculationResult(
			LocalDate.of(2026, 2, 28),
			LocalDate.of(2026, 2, 27),
			LocalDate.of(2026, 2, 28),
			2026,
			(short) 2
		);
		given(lastDayStrategy.calculate(schedule, targetMonth)).willReturn(expectedResult);

		PayrollEventCalculationResult result = payrollEventCalculationService.calculate(schedule, targetMonth);

		assertThat(result).isSameAs(expectedResult);
		verify(lastDayStrategy).calculate(schedule, targetMonth);
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если у правила не указан тип расписания")
	void calculateThrowsWhenScheduleTypeIsMissing() {
		MemberPayrollScheduleEntity schedule = schedule(null);

		assertThatThrownBy(() -> payrollEventCalculationService.calculate(schedule, YearMonth.of(2026, 3)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Payroll schedule type must not be null");
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если для типа правила нет зарегистрированной стратегии")
	void calculateThrowsWhenStrategyForScheduleTypeIsMissing() {
		PayrollEventCalculationService serviceWithoutLastDayStrategy = new PayrollEventCalculationService(List.of(fixedDayStrategy));
		MemberPayrollScheduleEntity schedule = schedule(PayrollScheduleType.LAST_DAY_OF_MONTH);

		assertThatThrownBy(() -> serviceWithoutLastDayStrategy.calculate(schedule, YearMonth.of(2026, 3)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No payroll calculation strategy registered for schedule type LAST_DAY_OF_MONTH");
	}

	@Test
	@DisplayName("Выбрасывает ошибку, если на один тип правила зарегистрировано несколько стратегий")
	void constructorThrowsWhenDuplicateStrategiesAreRegisteredForSingleScheduleType() {
		PayrollEventCalculationStrategy duplicateFixedDayStrategy = org.mockito.Mockito.mock(PayrollEventCalculationStrategy.class);
		given(duplicateFixedDayStrategy.supportedScheduleType()).willReturn(PayrollScheduleType.FIXED_DAY_OF_MONTH);

		assertThatThrownBy(() -> new PayrollEventCalculationService(
			List.of(fixedDayStrategy, duplicateFixedDayStrategy, lastDayStrategy)
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Duplicate payroll calculation strategy registered for schedule type FIXED_DAY_OF_MONTH");
	}

	private MemberPayrollScheduleEntity schedule(PayrollScheduleType scheduleType) {
		MemberPayrollScheduleEntity schedule = new MemberPayrollScheduleEntity();
		schedule.setScheduleType(scheduleType);
		return schedule;
	}
}


