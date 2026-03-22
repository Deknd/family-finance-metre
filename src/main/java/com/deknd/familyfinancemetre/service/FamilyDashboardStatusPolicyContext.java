package com.deknd.familyfinancemetre.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class FamilyDashboardStatusPolicyContext {

	private final int monthlyIncome;
	private final int monthlyExpenses;
	private final BigDecimal creditLoadPercent;
	private final BigDecimal emergencyFundMonths;
	private final int memberCountUsed;
}
