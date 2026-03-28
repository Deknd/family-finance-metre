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

/**
 * Месячный агрегированный снимок финансового состояния члена семьи.
 */
@Getter
@Setter
@Entity
@Table(name = "member_finance_snapshots")
public class MemberFinanceSnapshotEntity extends AbstractAuditableEntity {

	/**
	 * Семья, для которой хранится снимок.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	/**
	 * Пользователь, по которому рассчитан снимок.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	/**
	 * Самая свежая submission, определившая неаддитивные поля снимка.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_submission_id", nullable = false)
	private FinanceSubmissionEntity sourceSubmission;

	/**
	 * Год расчетного периода снимка.
	 */
	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	/**
	 * Месяц расчетного периода снимка.
	 */
	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	/**
	 * Суммарный доход пользователя за месяц.
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
	 * Актуальный объем ликвидных накоплений пользователя.
	 */
	@Column(name = "liquid_savings", nullable = false)
	private Integer liquidSavings;

	/**
	 * Время самой свежей submission, использованной для снимка.
	 */
	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;
}
