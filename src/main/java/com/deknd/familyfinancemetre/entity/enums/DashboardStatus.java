package com.deknd.familyfinancemetre.entity.enums;

import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DashboardStatus implements DatabaseEnum {
	NORMAL("normal"),
	WARNING("warning"),
	RISK("risk");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<DashboardStatus> {

		public JpaConverter() {
			super(DashboardStatus.class);
		}
	}
}
