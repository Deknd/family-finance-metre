package com.deknd.familyfinancemetre.entity;

import com.deknd.familyfinancemetre.entity.enums.DashboardStatus;
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

@Getter
@Setter
@Entity
@Table(name = "family_dashboard_snapshots")
public class FamilyDashboardSnapshotEntity extends AbstractAuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	@Column(name = "period_year", nullable = false)
	private Integer periodYear;

	@Column(name = "period_month", nullable = false)
	private Short periodMonth;

	@Convert(converter = DashboardStatus.JpaConverter.class)
	@Column(name = "status", nullable = false, length = 32)
	private DashboardStatus status;

	@Column(name = "status_text", nullable = false, length = 64)
	private String statusText;

	@Column(name = "status_reason", nullable = false)
	private String statusReason;

	@Column(name = "monthly_income", nullable = false)
	private Integer monthlyIncome;

	@Column(name = "monthly_expenses", nullable = false)
	private Integer monthlyExpenses;

	@Column(name = "credit_load_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal creditLoadPercent;

	@Column(name = "emergency_fund_months", nullable = false, precision = 8, scale = 2)
	private BigDecimal emergencyFundMonths;

	@Column(name = "member_count_used", nullable = false)
	private Integer memberCountUsed;

	@Column(name = "calculated_at", nullable = false)
	private OffsetDateTime calculatedAt;
}
