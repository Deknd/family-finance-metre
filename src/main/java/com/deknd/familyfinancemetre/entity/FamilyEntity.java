package com.deknd.familyfinancemetre.entity;

import com.deknd.familyfinancemetre.entity.enums.FamilyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Сущность семьи, для которой сервер хранит настройки и рассчитывает агрегаты.
 */
@Getter
@Setter
@Entity
@Table(name = "families")
public class FamilyEntity extends AbstractAuditableEntity {

	/**
	 * Название семьи.
	 */
	@Column(name = "name", nullable = false)
	private String name;

	/**
	 * Таймзона семьи, используемая в расчетах и отображении времени.
	 */
	@Column(name = "timezone", nullable = false, length = 64)
	private String timezone;

	/**
	 * Код валюты, в которой отображаются показатели семьи.
	 */
	@Column(name = "currency_code", nullable = false, length = 3)
	private String currencyCode;

	/**
	 * Текущий статус семьи в справочной модели.
	 */
	@Convert(converter = FamilyStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private FamilyStatus status;
}
