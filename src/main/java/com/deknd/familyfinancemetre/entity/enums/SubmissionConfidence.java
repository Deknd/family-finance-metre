package com.deknd.familyfinancemetre.entity.enums;

import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionConfidence implements DatabaseEnum {
	LOW("low"),
	MEDIUM("medium"),
	HIGH("high");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<SubmissionConfidence> {

		public JpaConverter() {
			super(SubmissionConfidence.class);
		}
	}
}
