package com.deknd.familyfinancemetre.core.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Базовый суперкласс для сущностей с UUID-идентификатором.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractUuidEntity {

	/**
	 * Уникальный идентификатор записи.
	 */
	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;
}

