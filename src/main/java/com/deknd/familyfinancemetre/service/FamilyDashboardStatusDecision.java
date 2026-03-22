package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.enums.DashboardStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class FamilyDashboardStatusDecision {

	private final DashboardStatus status;
	private final String statusText;
	private final String statusReason;
}
