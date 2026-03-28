package com.deknd.familyfinancemetre.flow.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserFinanceIntakeAcceptedResponse(
	@JsonProperty("status") String status,
	@JsonProperty("submission_id") String submissionId,
	@JsonProperty("family_id") String familyId,
	@JsonProperty("member_id") String memberId,
	@JsonProperty("recalculation_scheduled") boolean recalculationScheduled
) {
}

