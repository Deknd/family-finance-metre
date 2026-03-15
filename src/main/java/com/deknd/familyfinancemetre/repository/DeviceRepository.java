package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {
}
