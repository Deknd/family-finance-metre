package com.deknd.familyfinancemetre.core.device.repository;

import com.deknd.familyfinancemetre.core.device.entity.DeviceEntity;
import com.deknd.familyfinancemetre.core.device.enums.DeviceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

	@EntityGraph(attributePaths = "family")
	Optional<DeviceEntity> findByDeviceTokenHashAndStatus(String deviceTokenHash, DeviceStatus status);
}


