package com.deknd.familyfinancemetre.flow.intake.dto;

import com.deknd.familyfinancemetre.shared.validation.ValidIsoOffsetDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserFinanceIntakeRequest(
	@JsonProperty("external_submission_id") @NotBlank String externalSubmissionId,
	@JsonProperty("request_id") String requestId,
	@JsonProperty("family_id") @NotBlank @Pattern(
		regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
		message = "must be a valid UUID"
	) String familyId,
	@JsonProperty("member_id") @NotBlank @Pattern(
		regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
		message = "must be a valid UUID"
	) String memberId,
	@JsonProperty("source") @NotBlank @Pattern(regexp = "^telegram$", message = "must be one of: telegram") String source,
	@JsonProperty("collected_at") @NotBlank @ValidIsoOffsetDateTime String collectedAt,
	@JsonProperty("period") @NotNull @Valid Period period,
	@JsonProperty("finance_input") @NotNull @Valid FinanceInput financeInput,
	@JsonProperty("meta") @NotNull @Valid Meta meta
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Period(
		@JsonProperty("year") @NotNull @Positive Integer year,
		@JsonProperty("month") @NotNull @Min(1) @Max(12) Integer month
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record FinanceInput(
		@JsonProperty("monthly_income") @NotNull @PositiveOrZero Integer monthlyIncome,
		@JsonProperty("monthly_expenses") @NotNull @PositiveOrZero Integer monthlyExpenses,
		@JsonProperty("monthly_credit_payments") @NotNull @PositiveOrZero Integer monthlyCreditPayments,
		@JsonProperty("liquid_savings") @NotNull @PositiveOrZero Integer liquidSavings
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Meta(
		@JsonProperty("telegram_chat_id") @NotBlank String telegramChatId,
		@JsonProperty("confidence") @Pattern(
			regexp = "^(low|medium|high)$",
			message = "must be one of: low, medium, high"
		) String confidence,
		@JsonProperty("notes") String notes
	) {
	}
}


