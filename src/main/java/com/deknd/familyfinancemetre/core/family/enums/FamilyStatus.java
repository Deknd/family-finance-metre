package com.deknd.familyfinancemetre.core.family.enums;

import com.deknd.familyfinancemetre.core.common.persistence.AbstractDatabaseEnumConverter;
import com.deknd.familyfinancemetre.core.common.persistence.DatabaseEnum;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FamilyStatus implements DatabaseEnum {
	ACTIVE("active"),
	ARCHIVED("archived");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<FamilyStatus> {

		public JpaConverter() {
			super(FamilyStatus.class);
		}
	}
}

