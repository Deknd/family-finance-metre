package com.deknd.familyfinancemetre.service;

interface FamilyDashboardStatusPolicy {

	/**
	 * Определяет итоговый статус семьи по уже рассчитанным агрегированным метрикам dashboard.
	 *
	 * @param context агрегированные значения семьи за расчетный период
	 * @return итоговое решение по статусу, тексту и причине для snapshot
	 */
	FamilyDashboardStatusDecision evaluate(FamilyDashboardStatusPolicyContext context);
}
