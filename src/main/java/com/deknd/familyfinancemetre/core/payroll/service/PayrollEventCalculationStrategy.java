package com.deknd.familyfinancemetre.core.payroll.service;

import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;

import java.time.YearMonth;

interface PayrollEventCalculationStrategy {

	/**
	 * Возвращает тип зарплатного правила, который обслуживает текущая стратегия.
	 *
	 * @return поддерживаемый тип зарплатного правила
	 */
	PayrollScheduleType supportedScheduleType();

	/**
	 * Вычисляет payroll-событие для поддерживаемого типа зарплатного правила.
	 *
	 * @param schedule зарплатное правило участника семьи
	 * @param targetMonth целевой месяц расчета
	 * @return набор рассчитанных дат payroll-события и его расчетного периода
	 */
	PayrollEventCalculationResult calculate(MemberPayrollScheduleEntity schedule, YearMonth targetMonth);
}


