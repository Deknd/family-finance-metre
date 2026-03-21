package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.MemberFinanceSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberFinanceSnapshotRepository extends JpaRepository<MemberFinanceSnapshotEntity, UUID> {

	/**
	 * Ищет агрегированный месячный snapshot участника за указанный период.
	 *
	 * @param memberId идентификатор участника семьи
	 * @param periodYear год расчетного периода
	 * @param periodMonth месяц расчетного периода
	 * @return найденный snapshot или пустой результат, если запись еще не создана
	 */
	Optional<MemberFinanceSnapshotEntity> findByMemberIdAndPeriodYearAndPeriodMonth(
		UUID memberId,
		Integer periodYear,
		Short periodMonth
	);
}
