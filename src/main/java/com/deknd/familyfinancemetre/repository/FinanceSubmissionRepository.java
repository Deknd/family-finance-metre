package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinanceSubmissionRepository extends JpaRepository<FinanceSubmissionEntity, UUID> {
}
