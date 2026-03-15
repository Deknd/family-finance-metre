package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.FamilyDashboardSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyDashboardSnapshotRepository extends JpaRepository<FamilyDashboardSnapshotEntity, UUID> {
}
