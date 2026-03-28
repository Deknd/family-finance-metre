package com.deknd.familyfinancemetre.core.snapshot.enums;

import com.deknd.familyfinancemetre.core.common.persistence.AbstractDatabaseEnumConverter;
import com.deknd.familyfinancemetre.core.common.persistence.DatabaseEnum;
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

