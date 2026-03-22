package com.deknd.familyfinancemetre.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFamilyDashboardStatusPolicyTest {

	private final DefaultFamilyDashboardStatusPolicy policy = new DefaultFamilyDashboardStatusPolicy();

	@Test
	@DisplayName("Возвращает normal, когда кредитная нагрузка и подушка находятся в пределах нормы")
	void evaluateReturnsNormalWhenMetricsAreWithinNormalThresholds() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "27.00", "4.20"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("normal");
		assertThat(decision.getStatusText()).isEqualTo("Норма");
		assertThat(decision.getStatusReason()).isEqualTo("Показатели в пределах нормы");
	}

	@Test
	@DisplayName("Возвращает warning по подушке, когда она ниже трех месяцев")
	void evaluateReturnsWarningForEmergencyFund() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "27.00", "2.99"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("warning");
		assertThat(decision.getStatusText()).isEqualTo("Внимание");
		assertThat(decision.getStatusReason()).isEqualTo("Подушка ниже комфортной зоны");
	}

	@Test
	@DisplayName("Возвращает warning по кредитной нагрузке, когда она достигает порога предупреждения")
	void evaluateReturnsWarningForCreditLoad() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "30.00", "4.20"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("warning");
		assertThat(decision.getStatusText()).isEqualTo("Внимание");
		assertThat(decision.getStatusReason()).isEqualTo("Кредитная нагрузка выше комфортной");
	}

	@Test
	@DisplayName("Возвращает risk по подушке, когда она меньше одного месяца")
	void evaluateReturnsRiskForEmergencyFund() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "27.00", "0.99"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("risk");
		assertThat(decision.getStatusText()).isEqualTo("Риск");
		assertThat(decision.getStatusReason()).isEqualTo("Подушка меньше одного месяца");
	}

	@Test
	@DisplayName("Возвращает risk по кредитной нагрузке, когда она достигает зоны риска")
	void evaluateReturnsRiskForCreditLoad() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "50.00", "4.20"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("risk");
		assertThat(decision.getStatusText()).isEqualTo("Риск");
		assertThat(decision.getStatusReason()).isEqualTo("Кредитная нагрузка в зоне риска");
	}

	@Test
	@DisplayName("На границе три месяца подушки больше не возвращает warning")
	void evaluateDoesNotReturnWarningWhenEmergencyFundIsExactlyThreeMonths() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "27.00", "3.00"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("normal");
		assertThat(decision.getStatusReason()).isEqualTo("Показатели в пределах нормы");
	}

	@Test
	@DisplayName("На границе одного месяца подушки больше не возвращает risk")
	void evaluateDoesNotReturnRiskWhenEmergencyFundIsExactlyOneMonth() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "27.00", "1.00"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("warning");
		assertThat(decision.getStatusReason()).isEqualTo("Подушка ниже комфортной зоны");
	}

	@Test
	@DisplayName("На значении 29.99 процента кредитной нагрузки warning еще не наступает")
	void evaluateDoesNotReturnWarningWhenCreditLoadIsBelowWarningThreshold() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "29.99", "4.20"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("normal");
		assertThat(decision.getStatusReason()).isEqualTo("Показатели в пределах нормы");
	}

	@Test
	@DisplayName("На значении 49.99 процента кредитной нагрузки risk еще не наступает")
	void evaluateDoesNotReturnRiskWhenCreditLoadIsBelowRiskThreshold() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "49.99", "4.20"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("warning");
		assertThat(decision.getStatusReason()).isEqualTo("Кредитная нагрузка выше комфортной");
	}

	@Test
	@DisplayName("При нескольких warning-факторах показывает причину по подушке как более приоритетную")
	void evaluatePrioritizesEmergencyFundReasonWhenMultipleWarningRulesMatch() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "35.00", "2.50"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("warning");
		assertThat(decision.getStatusReason()).isEqualTo("Подушка ниже комфортной зоны");
	}

	@Test
	@DisplayName("При нескольких risk-факторах показывает причину по подушке как более приоритетную")
	void evaluatePrioritizesEmergencyFundReasonWhenMultipleRiskRulesMatch() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 90000, "55.00", "0.80"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("risk");
		assertThat(decision.getStatusReason()).isEqualTo("Подушка меньше одного месяца");
	}

	@Test
	@DisplayName("Не применяет правило подушки при нулевых расходах")
	void evaluateIgnoresEmergencyFundRuleWhenExpensesAreZero() {
		FamilyDashboardStatusDecision decision = policy.evaluate(context(210000, 0, "0.00", "0.00"));

		assertThat(decision.getStatus().getDatabaseValue()).isEqualTo("normal");
		assertThat(decision.getStatusReason()).isEqualTo("Показатели в пределах нормы");
	}

	private FamilyDashboardStatusPolicyContext context(
		int monthlyIncome,
		int monthlyExpenses,
		String creditLoadPercent,
		String emergencyFundMonths
	) {
		return new FamilyDashboardStatusPolicyContext(
			monthlyIncome,
			monthlyExpenses,
			new BigDecimal(creditLoadPercent),
			new BigDecimal(emergencyFundMonths),
			2
		);
	}
}
