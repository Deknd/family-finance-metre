package com.deknd.familyfinancemetre.core.payroll.service;

import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Координирует расчет payroll-события и выбирает стратегию по типу зарплатного правила.
 */
@Service
public class PayrollEventCalculationService {

	private final Map<PayrollScheduleType, PayrollEventCalculationStrategy> strategiesByScheduleType;

	PayrollEventCalculationService(List<PayrollEventCalculationStrategy> strategies) {
		EnumMap<PayrollScheduleType, PayrollEventCalculationStrategy> strategiesByType =
			new EnumMap<>(PayrollScheduleType.class);

		for (PayrollEventCalculationStrategy strategy : strategies) {
			PayrollScheduleType supportedScheduleType = Objects.requireNonNull(
				strategy.supportedScheduleType(),
				"Payroll calculation strategy supportedScheduleType must not be null"
			);
			PayrollEventCalculationStrategy existingStrategy = strategiesByType.putIfAbsent(
				supportedScheduleType,
				strategy
			);
			if (existingStrategy != null) {
				throw new IllegalStateException(
					"Duplicate payroll calculation strategy registered for schedule type " + supportedScheduleType
				);
			}
		}

		this.strategiesByScheduleType = Map.copyOf(strategiesByType);
	}

	/**
	 * Вычисляет даты payroll-события и расчетный период для указанного зарплатного правила.
	 *
	 * @param schedule зарплатное правило участника семьи
	 * @param targetMonth целевой месяц, для которого нужно вычислить событие
	 * @return набор рассчитанных дат payroll-события и его расчетного периода
	 */
	public PayrollEventCalculationResult calculate(MemberPayrollScheduleEntity schedule, YearMonth targetMonth) {
		Objects.requireNonNull(schedule, "Payroll schedule must not be null");
		Objects.requireNonNull(targetMonth, "Target month must not be null");

		PayrollScheduleType scheduleType = schedule.getScheduleType();
		if (scheduleType == null) {
			throw new IllegalStateException("Payroll schedule type must not be null");
		}

		PayrollEventCalculationStrategy strategy = strategiesByScheduleType.get(scheduleType);
		if (strategy == null) {
			throw new IllegalStateException("No payroll calculation strategy registered for schedule type " + scheduleType);
		}

		return strategy.calculate(schedule, targetMonth);
	}
}


