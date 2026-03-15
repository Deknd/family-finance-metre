package com.deknd.familyfinancemetre.entity.enums;

import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

public abstract class AbstractDatabaseEnumConverter<E extends Enum<E> & DatabaseEnum> implements AttributeConverter<E, String> {

	private final Class<E> enumType;

	protected AbstractDatabaseEnumConverter(Class<E> enumType) {
		this.enumType = enumType;
	}

	@Override
	public String convertToDatabaseColumn(E attribute) {
		return attribute == null ? null : attribute.getDatabaseValue();
	}

	@Override
	public E convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}

		return Arrays.stream(enumType.getEnumConstants())
			.filter(candidate -> candidate.getDatabaseValue().equals(dbData))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown enum value '%s' for %s".formatted(dbData, enumType.getSimpleName())));
	}
}
