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

@Getter
@Setter
@Entity
@Table(name = "llm_collection_requests")
public class LlmCollectionRequestEntity extends AbstractAuditableEntity {

	@Column(name = "request_id", nullable = false)
	private String requestId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payroll_schedule_id", nullable = false)
	private MemberPayrollScheduleEntity payrollSchedule;

	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	@Convert(converter = LlmCollectionRequestReason.JpaConverter.class)
	@Column(name = "reason", nullable = false, length = 64)
	private LlmCollectionRequestReason reason;

	@Convert(converter = LlmCollectionRequestStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private LlmCollectionRequestStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "requested_fields", nullable = false, columnDefinition = "jsonb")
	private JsonNode requestedFields;

	@Column(name = "nominal_payroll_date", nullable = false)
	private LocalDate nominalPayrollDate;

	@Column(name = "effective_payroll_date", nullable = false)
	private LocalDate effectivePayrollDate;

	@Column(name = "scheduled_trigger_date", nullable = false)
	private LocalDate scheduledTriggerDate;

	@Column(name = "triggered_at", nullable = false)
	private OffsetDateTime triggeredAt;

	@Column(name = "accepted_at")
	private OffsetDateTime acceptedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "workflow_run_id")
	private String workflowRunId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
	private JsonNode requestPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response_payload", columnDefinition = "jsonb")
	private JsonNode responsePayload;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;
}
