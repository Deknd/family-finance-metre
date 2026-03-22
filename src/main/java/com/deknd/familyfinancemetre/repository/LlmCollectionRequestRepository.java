package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.LlmCollectionRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
