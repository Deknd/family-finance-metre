package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.LlmCollectionRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LlmCollectionRequestRepository extends JpaRepository<LlmCollectionRequestEntity, UUID> {
}
