package com.deknd.familyfinancemetre.dto.device;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceDashboardResponse(
	OffsetDateTime generatedAt,
	String deviceId,
	String familyId,
	String status,
	String statusText,
	String statusReason,
	Metrics metrics,
	Display display
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Metrics(
		Integer monthlyIncome,
		Integer monthlyExpenses,
		BigDecimal creditLoadPercent,
		BigDecimal emergencyFundMonths
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Display(
		String currency,
		String updatedAtLabel
	) {
	}
}
