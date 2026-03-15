package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.FamilyMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, UUID> {
}
