package com.deknd.familyfinancemetre.entity;

import com.deknd.familyfinancemetre.entity.enums.LlmCollectionRequestReason;
import com.deknd.familyfinancemetre.entity.enums.LlmCollectionRequestStatus;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Сущность запуска LLM-опроса по конкретному зарплатному событию.
 */
@Getter
@Setter
@Entity
@Table(name = "llm_collection_requests")
public class LlmCollectionRequestEntity extends AbstractAuditableEntity {

	/**
	 * Основной correlation id цепочки server -> n8n -> Telegram -> server.
	 */
	@Column(name = "request_id", nullable = false)
	private String requestId;

	/**
	 * Семья, для которой инициирован сбор данных.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	/**
	 * Пользователь, которого необходимо опросить.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	/**
	 * Правило выплаты, породившее запуск опроса.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payroll_schedule_id", nullable = false)
	private MemberPayrollScheduleEntity payrollSchedule;

	/**
	 * Год расчетного периода, для которого собираются данные.
	 */
	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	/**
	 * Месяц расчетного периода, для которого собираются данные.
	 */
	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	/**
	 * Причина запуска опроса.
	 */
	@Convert(converter = LlmCollectionRequestReason.JpaConverter.class)
	@Column(name = "reason", nullable = false, length = 64)
	private LlmCollectionRequestReason reason;

	/**
	 * Текущий статус жизненного цикла интеграционного запроса.
	 */
	@Convert(converter = LlmCollectionRequestStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private LlmCollectionRequestStatus status;

	/**
	 * Список полей, которые нужно собрать в рамках опроса.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "requested_fields", nullable = false, columnDefinition = "jsonb")
	private JsonNode requestedFields;

	/**
	 * Номинальная дата выплаты без учета переносов.
	 */
	@Column(name = "nominal_payroll_date", nullable = false)
	private LocalDate nominalPayrollDate;

	/**
	 * Фактическая дата выплаты после применения правил переноса.
	 */
	@Column(name = "effective_payroll_date", nullable = false)
	private LocalDate effectivePayrollDate;

	/**
	 * Дата, в которую сервер должен запустить сбор данных.
	 */
	@Column(name = "scheduled_trigger_date", nullable = false)
	private LocalDate scheduledTriggerDate;

	/**
	 * Время отправки запроса в интеграционный контур.
	 */
	@Column(name = "triggered_at", nullable = false)
	private OffsetDateTime triggeredAt;

	/**
	 * Время, когда n8n подтвердил прием запроса.
	 */
	@Column(name = "accepted_at")
	private OffsetDateTime acceptedAt;

	/**
	 * Время успешного завершения всей цепочки сбора данных.
	 */
	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	/**
	 * Идентификатор workflow run на стороне n8n.
	 */
	@Column(name = "workflow_run_id")
	private String workflowRunId;

	/**
	 * Payload, который сервер отправил в n8n.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
	private JsonNode requestPayload;

	/**
	 * Ответный payload от n8n при успешном запуске workflow.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response_payload", columnDefinition = "jsonb")
	private JsonNode responsePayload;

	/**
	 * Текст ошибки интеграции, если запуск или обработка завершились неуспешно.
	 */
	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;
}
