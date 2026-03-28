package com.deknd.familyfinancemetre.entity;

import com.deknd.familyfinancemetre.entity.enums.DeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Сущность физического устройства, которое получает семейный dashboard.
 */
@Getter
@Setter
@Entity
@Table(name = "devices")
public class DeviceEntity extends AbstractAuditableEntity {

	/**
	 * Семья, к которой привязано устройство.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	/**
	 * Человекочитаемое имя устройства.
	 */
	@Column(name = "name", nullable = false)
	private String name;

	/**
	 * Хэш токена устройства для аутентификации запросов.
	 */
	@Column(name = "device_token_hash", nullable = false)
	private String deviceTokenHash;

	/**
	 * Текущий статус доступности устройства.
	 */
	@Convert(converter = DeviceStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private DeviceStatus status;

	/**
	 * Время последнего успешного обращения устройства к серверу.
	 */
	@Column(name = "last_seen_at")
	private OffsetDateTime lastSeenAt;
}
