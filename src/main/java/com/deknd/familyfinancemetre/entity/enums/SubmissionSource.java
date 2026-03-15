package com.deknd.familyfinancemetre.entity.enums;

import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionSource implements DatabaseEnum {
	TELEGRAM("telegram");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<SubmissionSource> {

		public JpaConverter() {
			super(SubmissionSource.class);
		}
	}
}
