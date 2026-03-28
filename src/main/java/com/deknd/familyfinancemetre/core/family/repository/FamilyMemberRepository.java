package com.deknd.familyfinancemetre.core.family.repository;

import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, UUID> {
}


