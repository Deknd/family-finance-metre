package com.deknd.familyfinancemetre.flow.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceDashboardResponse(
	@JsonProperty("generated_at") OffsetDateTime generatedAt,
	@JsonProperty("device_id") String deviceId,
	@JsonProperty("family_id") String familyId,
	@JsonProperty("status") String status,
	@JsonProperty("status_text") String statusText,
	@JsonProperty("status_reason") String statusReason,
	@JsonProperty("metrics") Metrics metrics,
	@JsonProperty("display") Display display
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Metrics(
		@JsonProperty("monthly_income") Integer monthlyIncome,
		@JsonProperty("monthly_expenses") Integer monthlyExpenses,
		@JsonProperty("credit_load_percent") BigDecimal creditLoadPercent,
		@JsonProperty("emergency_fund_months") BigDecimal emergencyFundMonths
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Display(
		@JsonProperty("currency") String currency,
		@JsonProperty("updated_at_label") String updatedAtLabel
	) {
	}
}

