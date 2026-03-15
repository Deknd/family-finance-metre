package com.deknd.familyfinancemetre.entity;

import com.deknd.familyfinancemetre.entity.enums.SubmissionConfidence;
import com.deknd.familyfinancemetre.entity.enums.SubmissionSource;
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

@Getter
@Setter
@Entity
@Table(name = "finance_submissions")
public class FinanceSubmissionEntity extends AbstractCreatedEntity {

	@Column(name = "external_submission_id", nullable = false)
	private String externalSubmissionId;

	@Column(name = "request_id")
	private String requestId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "llm_collection_request_id")
	private LlmCollectionRequestEntity llmCollectionRequest;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	@Convert(converter = SubmissionSource.JpaConverter.class)
	@Column(name = "source", nullable = false, length = 32)
	private SubmissionSource source;

	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;

	@Column(name = "monthly_income", nullable = false)
	private Integer monthlyIncome;

	@Column(name = "monthly_expenses", nullable = false)
	private Integer monthlyExpenses;

	@Column(name = "monthly_credit_payments", nullable = false)
	private Integer monthlyCreditPayments;

	@Column(name = "liquid_savings", nullable = false)
	private Integer liquidSavings;

	@Convert(converter = SubmissionConfidence.JpaConverter.class)
	@Column(name = "confidence", length = 16)
	private SubmissionConfidence confidence;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
	private JsonNode rawPayload;
}
