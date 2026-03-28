package com.deknd.familyfinancemetre.core.snapshot.repository;

import com.deknd.familyfinancemetre.core.snapshot.entity.FinanceSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinanceSubmissionRepository extends JpaRepository<FinanceSubmissionEntity, UUID> {

	/**
	 * Проверяет, был ли уже сохранен intake payload с указанным внешним идентификатором отправки.
	 *
	 * @param externalSubmissionId уникальный идентификатор отправки со стороны n8n
	 * @return {@code true}, если запись с таким external submission id уже существует
	 */
	boolean existsByExternalSubmissionId(String externalSubmissionId);

	/**
	 * Возвращает все intake submission участника за указанный период, отсортированные
	 * от самой свежей записи к самой старой.
	 *
	 * @param memberId идентификатор участника семьи
	 * @param periodYear год расчетного периода
	 * @param periodMonth месяц расчетного периода
	 * @return список submission за период в порядке убывания актуальности
	 */
	List<FinanceSubmissionEntity> findAllByMemberIdAndPeriodYearAndPeriodMonthOrderByCollectedAtDescCreatedAtDescIdDesc(
		UUID memberId,
		Integer periodYear,
		Short periodMonth
	);
}


