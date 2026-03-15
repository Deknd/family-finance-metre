package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.MemberFinanceSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemberFinanceSnapshotRepository extends JpaRepository<MemberFinanceSnapshotEntity, UUID> {
}
