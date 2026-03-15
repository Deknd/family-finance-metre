package com.deknd.familyfinancemetre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class AbstractCreatedEntity extends AbstractUuidEntity {

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;
}
