package com.deknd.familyfinancemetre.core.snapshot.repository;

import com.deknd.familyfinancemetre.core.snapshot.entity.FamilyDashboardSnapshotEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FamilyDashboardSnapshotRepository extends JpaRepository<FamilyDashboardSnapshotEntity, UUID> {

	/**
	 * Ищет семейный dashboard snapshot за указанный период.
	 *
	 * @param familyId идентификатор семьи
	 * @param periodYear год расчетного периода
	 * @param periodMonth месяц расчетного периода
	 * @return найденный snapshot или пустой результат, если запись еще не создана
	 */
	Optional<FamilyDashboardSnapshotEntity> findByFamilyIdAndPeriodYearAndPeriodMonth(
		UUID familyId,
		Integer periodYear,
		Short periodMonth
	);

	/**
	 * Ищет актуальный семейный dashboard snapshot за последний доступный расчетный период.
	 *
	 * @param familyId идентификатор семьи
	 * @return найденный актуальный snapshot или пустой результат, если для семьи еще нет расчетных данных
	 */
	@EntityGraph(attributePaths = "family")
	Optional<FamilyDashboardSnapshotEntity> findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(UUID familyId);
}


