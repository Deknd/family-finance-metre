package com.deknd.familyfinancemetre.core.collection.enums;

import com.deknd.familyfinancemetre.core.common.persistence.AbstractDatabaseEnumConverter;
import com.deknd.familyfinancemetre.core.common.persistence.DatabaseEnum;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LlmCollectionRequestStatus implements DatabaseEnum {
	PENDING("pending"),
	ACCEPTED("accepted"),
	COMPLETED("completed"),
	FAILED("failed"),
	CANCELLED("cancelled");

	private final String databaseValue;

	@Converter(autoApply = false)
	public static class JpaConverter extends AbstractDatabaseEnumConverter<LlmCollectionRequestStatus> {

		public JpaConverter() {
			super(LlmCollectionRequestStatus.class);
		}
	}
}

