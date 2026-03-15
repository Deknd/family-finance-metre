package com.deknd.familyfinancemetre.entity.enums;

import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LlmCollectionRequestReason implements DatabaseEnum {
	DAY_AFTER_SALARY("day_after_salary");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<LlmCollectionRequestReason> {

		public JpaConverter() {
			super(LlmCollectionRequestReason.class);
		}
	}
}
