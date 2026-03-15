package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.MemberPayrollScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemberPayrollScheduleRepository extends JpaRepository<MemberPayrollScheduleEntity, UUID> {
}
