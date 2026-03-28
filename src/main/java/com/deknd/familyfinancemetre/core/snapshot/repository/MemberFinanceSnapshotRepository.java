package com.deknd.familyfinancemetre.core.snapshot.repository;

import com.deknd.familyfinancemetre.core.snapshot.entity.MemberFinanceSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

	/**
	 * Возвращает все месячные snapshot-ы членов семьи за указанный период.
	 *
	 * @param familyId идентификатор семьи
	 * @param periodYear год расчетного периода
	 * @param periodMonth месяц расчетного периода
	 * @return список snapshot-ов семьи за период
	 */
	List<MemberFinanceSnapshotEntity> findAllByFamilyIdAndPeriodYearAndPeriodMonth(
		UUID familyId,
		Integer periodYear,
		Short periodMonth
	);
}


