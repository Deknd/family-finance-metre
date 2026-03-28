package com.deknd.familyfinancemetre.core.snapshot.service;

import com.deknd.familyfinancemetre.core.snapshot.enums.DashboardStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
class DefaultFamilyDashboardStatusPolicy implements FamilyDashboardStatusPolicy {

	private static final BigDecimal WARNING_CREDIT_LOAD_THRESHOLD = new BigDecimal("30.00");
	private static final BigDecimal RISK_CREDIT_LOAD_THRESHOLD = new BigDecimal("50.00");
	private static final BigDecimal WARNING_EMERGENCY_FUND_THRESHOLD = new BigDecimal("3.00");
	private static final BigDecimal RISK_EMERGENCY_FUND_THRESHOLD = new BigDecimal("1.00");

	private static final FamilyDashboardStatusDecision NORMAL_DECISION = new FamilyDashboardStatusDecision(
		DashboardStatus.NORMAL,
		"Норма",
		"Показатели в пределах нормы"
	);
	private static final FamilyDashboardStatusDecision WARNING_EMERGENCY_FUND_DECISION = new FamilyDashboardStatusDecision(
		DashboardStatus.WARNING,
		"Внимание",
		"Подушка ниже комфортной зоны"
	);
	private static final FamilyDashboardStatusDecision WARNING_CREDIT_LOAD_DECISION = new FamilyDashboardStatusDecision(
		DashboardStatus.WARNING,
		"Внимание",
		"Кредитная нагрузка выше комфортной"
	);
	private static final FamilyDashboardStatusDecision RISK_EMERGENCY_FUND_DECISION = new FamilyDashboardStatusDecision(
		DashboardStatus.RISK,
		"Риск",
		"Подушка меньше одного месяца"
	);
	private static final FamilyDashboardStatusDecision RISK_CREDIT_LOAD_DECISION = new FamilyDashboardStatusDecision(
		DashboardStatus.RISK,
		"Риск",
		"Кредитная нагрузка в зоне риска"
	);

	/**
	 * Выбирает итоговый статус семьи по фиксированной MVP-policy.
	 *
	 * @param context агрегированные значения семьи за расчетный период
	 * @return итоговое решение по статусу, тексту и причине для snapshot
	 */
	@Override
	public FamilyDashboardStatusDecision evaluate(FamilyDashboardStatusPolicyContext context) {
		if (isRiskEmergencyFund(context)) {
			return RISK_EMERGENCY_FUND_DECISION;
		}
		if (isRiskCreditLoad(context)) {
			return RISK_CREDIT_LOAD_DECISION;
		}
		if (isWarningEmergencyFund(context)) {
			return WARNING_EMERGENCY_FUND_DECISION;
		}
		if (isWarningCreditLoad(context)) {
			return WARNING_CREDIT_LOAD_DECISION;
		}
		return NORMAL_DECISION;
	}

	private boolean isRiskEmergencyFund(FamilyDashboardStatusPolicyContext context) {
		return hasExpenses(context)
			&& context.getEmergencyFundMonths().compareTo(RISK_EMERGENCY_FUND_THRESHOLD) < 0;
	}

	private boolean isRiskCreditLoad(FamilyDashboardStatusPolicyContext context) {
		return context.getCreditLoadPercent().compareTo(RISK_CREDIT_LOAD_THRESHOLD) >= 0;
	}

	private boolean isWarningEmergencyFund(FamilyDashboardStatusPolicyContext context) {
		return hasExpenses(context)
			&& context.getEmergencyFundMonths().compareTo(WARNING_EMERGENCY_FUND_THRESHOLD) < 0;
	}

	private boolean isWarningCreditLoad(FamilyDashboardStatusPolicyContext context) {
		return context.getCreditLoadPercent().compareTo(WARNING_CREDIT_LOAD_THRESHOLD) >= 0;
	}

	private boolean hasExpenses(FamilyDashboardStatusPolicyContext context) {
		return context.getMonthlyExpenses() > 0;
	}
}


