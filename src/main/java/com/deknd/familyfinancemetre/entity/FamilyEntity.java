package com.deknd.familyfinancemetre.entity;

import com.deknd.familyfinancemetre.entity.enums.FamilyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "families")
public class FamilyEntity extends AbstractAuditableEntity {

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "timezone", nullable = false, length = 64)
	private String timezone;

	@Column(name = "currency_code", nullable = false, length = 3)
	private String currencyCode;

	@Convert(converter = FamilyStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private FamilyStatus status;
}
