package com.deknd.familyfinancemetre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "member_finance_snapshots")
public class MemberFinanceSnapshotEntity extends AbstractAuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_submission_id", nullable = false)
	private FinanceSubmissionEntity sourceSubmission;

	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	@Column(name = "monthly_income", nullable = false)
	private Integer monthlyIncome;

	@Column(name = "monthly_expenses", nullable = false)
	private Integer monthlyExpenses;

	@Column(name = "monthly_credit_payments", nullable = false)
	private Integer monthlyCreditPayments;

	@Column(name = "liquid_savings", nullable = false)
	private Integer liquidSavings;

	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;
}
