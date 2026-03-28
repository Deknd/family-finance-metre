package com.deknd.familyfinancemetre.core.snapshot.entity;

import com.deknd.familyfinancemetre.core.common.entity.AbstractAuditableEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.snapshot.enums.DashboardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Готовый семейный dashboard-снимок для отдачи физическому устройству.
 */
@Getter
@Setter
@Entity
@Table(name = "family_dashboard_snapshots")
public class FamilyDashboardSnapshotEntity extends AbstractAuditableEntity {

	/**
	 * Семья, для которой рассчитан dashboard.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	/**
	 * Год расчетного периода dashboard.
	 */
	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	/**
	 * Месяц расчетного периода dashboard.
	 */
	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	/**
	 * Машинное значение итогового статуса семьи.
	 */
	@Convert(converter = DashboardStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private DashboardStatus status;

	/**
	 * Короткий текст статуса для интерфейса устройства.
	 */
	@Column(name = "status_text", nullable = false, length = 64)
	private String statusText;

	/**
	 * Причина выбранного статуса для второй строки экрана.
	 */
	@Column(name = "status_reason", nullable = false)
	private String statusReason;

	/**
	 * Суммарный доход семьи за месяц.
	 */
	@Column(name = "monthly_income", nullable = false)
	private Integer monthlyIncome;

	/**
	 * Актуальная оценка месячных расходов семьи.
	 */
	@Column(name = "monthly_expenses", nullable = false)
	private Integer monthlyExpenses;

	/**
	 * Кредитная нагрузка семьи в процентах.
	 */
	@Column(name = "credit_load_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal creditLoadPercent;

	/**
	 * Размер подушки безопасности семьи в месяцах расходов.
	 */
	@Column(name = "emergency_fund_months", nullable = false, precision = 8, scale = 2)
	private BigDecimal emergencyFundMonths;

	/**
	 * Количество участников семьи, включенных в расчет dashboard.
	 */
	@Column(name = "member_count_used", nullable = false)
	private Integer memberCountUsed;

	/**
	 * Время фактического расчета dashboard.
	 */
	@Column(name = "calculated_at", nullable = false)
	private OffsetDateTime calculatedAt;
}


