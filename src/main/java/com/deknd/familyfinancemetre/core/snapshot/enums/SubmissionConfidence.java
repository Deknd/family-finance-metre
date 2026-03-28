package com.deknd.familyfinancemetre.core.snapshot.enums;

import com.deknd.familyfinancemetre.core.common.persistence.AbstractDatabaseEnumConverter;
import com.deknd.familyfinancemetre.core.common.persistence.DatabaseEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionConfidence implements DatabaseEnum {
	@JsonProperty("low")
	LOW("low"),
	@JsonProperty("medium")
	MEDIUM("medium"),
	@JsonProperty("high")
	HIGH("high");

	private final String databaseValue;

	@Override
	@JsonValue
	public String getDatabaseValue() {
		return databaseValue;
	}

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<SubmissionConfidence> {

		public JpaConverter() {
			super(SubmissionConfidence.class);
		}
	}
}

