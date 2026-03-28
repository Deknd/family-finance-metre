package com.deknd.familyfinancemetre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Базовый суперкласс для сущностей с датой создания.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractCreatedEntity extends AbstractUuidEntity {

	/**
	 * Момент создания записи.
	 */
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;
}
