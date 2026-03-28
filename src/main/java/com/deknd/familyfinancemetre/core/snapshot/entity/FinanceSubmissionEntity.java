package com.deknd.familyfinancemetre.core.snapshot.entity;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.common.entity.AbstractCreatedEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.snapshot.enums.SubmissionConfidence;
import com.deknd.familyfinancemetre.core.snapshot.enums.SubmissionSource;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Сущность результата intake-опроса с финансовыми данными пользователя.
 */
@Getter
@Setter
@Entity
@Table(name = "finance_submissions")
public class FinanceSubmissionEntity extends AbstractCreatedEntity {

	/**
	 * Внешний идентификатор submission со стороны интеграции.
	 */
	@Column(name = "external_submission_id", nullable = false)
	private String externalSubmissionId;

	/**
	 * Correlation id исходного запуска опроса.
	 */
	@Column(name = "request_id")
	private String requestId;

	/**
	 * Запрос на сбор данных, с которым связан intake при наличии request id.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "llm_collection_request_id")
	private LlmCollectionRequestEntity llmCollectionRequest;

	/**
	 * Семья, к которой относятся присланные данные.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	/**
	 * Пользователь, которого опрашивали.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	/**
	 * Канал, из которого пришли данные.
	 */
	@Convert(converter = SubmissionSource.JpaConverter.class)
	@Column(name = "source", nullable = false, length = 32)
	private SubmissionSource source;

	/**
	 * Год расчетного периода, к которому относится ответ.
	 */
	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	/**
	 * Месяц расчетного периода, к которому относится ответ.
	 */
	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	/**
	 * Время завершения опроса и фиксации значений пользователем.
	 */
	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;

	/**
	 * Доход по конкретному payroll-событию.
	 */
	@Column(name = "monthly_income", nullable = false)
	private Integer monthlyIncome;

	/**
	 * Актуальная оценка расходов пользователя за месяц.
	 */
	@Column(name = "monthly_expenses", nullable = false)
	private Integer monthlyExpenses;

	/**
	 * Актуальная оценка кредитных платежей пользователя за месяц.
	 */
	@Column(name = "monthly_credit_payments", nullable = false)
	private Integer monthlyCreditPayments;

	/**
	 * Объем доступных накоплений пользователя на момент опроса.
	 */
	@Column(name = "liquid_savings", nullable = false)
	private Integer liquidSavings;

	/**
	 * Оценка уверенности в корректности полученных значений.
	 */
	@Convert(converter = SubmissionConfidence.JpaConverter.class)
	@Column(name = "confidence", length = 16)
	private SubmissionConfidence confidence;

	/**
	 * Дополнительное текстовое пояснение к ответу пользователя.
	 */
	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	/**
	 * Исходный payload intake в нормализованном JSON-формате.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
	private JsonNode rawPayload;
}


