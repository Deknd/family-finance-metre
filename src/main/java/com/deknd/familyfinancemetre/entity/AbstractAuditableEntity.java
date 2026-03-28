package com.deknd.familyfinancemetre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;

/**
 * Базовый суперкласс для сущностей с полями аудита создания и обновления.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractAuditableEntity extends AbstractCreatedEntity {

	/**
	 * Момент последнего изменения записи.
	 */
	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
