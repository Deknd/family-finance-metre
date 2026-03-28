package com.deknd.familyfinancemetre.core.device.enums;

import com.deknd.familyfinancemetre.core.common.persistence.AbstractDatabaseEnumConverter;
import com.deknd.familyfinancemetre.core.common.persistence.DatabaseEnum;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeviceStatus implements DatabaseEnum {
	ACTIVE("active"),
	DISABLED("disabled");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<DeviceStatus> {

		public JpaConverter() {
			super(DeviceStatus.class);
		}
	}
}

