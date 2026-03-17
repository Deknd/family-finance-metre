package com.deknd.familyfinancemetre.repository;

import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinanceSubmissionRepository extends JpaRepository<FinanceSubmissionEntity, UUID> {

	/**
	 * Проверяет, был ли уже сохранен intake payload с указанным внешним идентификатором отправки.
	 *
	 * @param externalSubmissionId уникальный идентификатор отправки со стороны n8n
	 * @return {@code true}, если запись с таким external submission id уже существует
	 */
	boolean existsByExternalSubmissionId(String externalSubmissionId);
}
