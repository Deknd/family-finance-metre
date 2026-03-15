package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.FamilyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyRepository extends JpaRepository<FamilyEntity, UUID> {
}
