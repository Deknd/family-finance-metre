package com.deknd.familyfinancemetre.core.collection.repository;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LlmCollectionRequestRepository extends JpaRepository<LlmCollectionRequestEntity, UUID> {

	/**
	 * Возвращает запрос на запуск сбора данных по его correlation identifier.
	 *
	 * @param requestId идентификатор запуска, передаваемый через всю цепочку интеграции
	 * @return найденный запрос или пустой результат, если запись отсутствует
	 */
	Optional<LlmCollectionRequestEntity> findByRequestId(String requestId);

	/**
	 * Возвращает запрос на сбор данных по конкретному payroll-событию.
	 *
	 * @param payrollScheduleId идентификатор правила выплаты
	 * @param effectivePayrollDate фактическая дата выплаты после переноса выходных
	 * @return найденный запрос или пустой результат, если для события запись еще не создана
	 */
	Optional<LlmCollectionRequestEntity> findByPayrollScheduleIdAndEffectivePayrollDate(
		UUID payrollScheduleId,
		LocalDate effectivePayrollDate
	);
}


