package com.deknd.familyfinancemetre.core.payroll.service;

import java.time.LocalDate;

/**
 * Результат расчета payroll-события для конкретного правила и расчетного месяца.
 *
 * @param nominalPayrollDate номинальная дата выплаты без учета переноса выходных
 * @param effectivePayrollDate фактическая дата выплаты после применения правила переноса
 * @param scheduledTriggerDate дата запуска опроса после выплаты
 * @param periodYear год расчетного периода
 * @param periodMonth месяц расчетного периода
 */
public record PayrollEventCalculationResult(
	LocalDate nominalPayrollDate,
	LocalDate effectivePayrollDate,
	LocalDate scheduledTriggerDate,
	int periodYear,
	short periodMonth
) {
}

