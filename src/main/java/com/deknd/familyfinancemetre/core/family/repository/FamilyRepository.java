package com.deknd.familyfinancemetre.core.family.repository;

import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyRepository extends JpaRepository<FamilyEntity, UUID> {
}


