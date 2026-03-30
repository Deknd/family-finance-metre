package com.deknd.familyfinancemetre.core.payroll.repository;

import com.deknd.familyfinancemetre.core.family.enums.FamilyStatus;
import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberPayrollScheduleRepository extends JpaRepository<MemberPayrollScheduleEntity, UUID> {

	/**
	 * Возвращает активные payroll-правила для автоматического запуска опросов
	 * вместе с уже загруженными участником семьи и семьей.
	 *
	 * @param familyStatus статус семьи, разрешенный для автоматического запуска
	 * @return список eligible payroll-правил для daily scheduler
	 */
	@EntityGraph(attributePaths = {"member", "member.family"})
	List<MemberPayrollScheduleEntity> findAllByActiveTrueAndMemberActiveTrueAndMemberFamilyStatus(FamilyStatus familyStatus);
}


