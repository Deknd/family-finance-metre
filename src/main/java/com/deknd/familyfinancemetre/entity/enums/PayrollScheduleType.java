package com.deknd.familyfinancemetre.entity.enums;

import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayrollScheduleType implements DatabaseEnum {
	FIXED_DAY_OF_MONTH("fixed_day_of_month"),
	LAST_DAY_OF_MONTH("last_day_of_month");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<PayrollScheduleType> {

		public JpaConverter() {
			super(PayrollScheduleType.class);
		}
	}
}
