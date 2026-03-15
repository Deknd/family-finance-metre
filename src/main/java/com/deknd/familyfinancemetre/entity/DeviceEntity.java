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

@Getter
@Setter
@Entity
@Table(name = "devices")
public class DeviceEntity extends AbstractAuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "device_token_hash", nullable = false)
	private String deviceTokenHash;

	@Convert(converter = DeviceStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private DeviceStatus status;

	@Column(name = "last_seen_at")
	private OffsetDateTime lastSeenAt;
}
