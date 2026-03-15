package com.deknd.familyfinancemetre.entity;

import com.deknd.familyfinancemetre.entity.enums.PayrollScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "member_payroll_schedules")
public class MemberPayrollScheduleEntity extends AbstractAuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	@Column(name = "label")
	private String label;

	@Convert(converter = PayrollScheduleType.JpaConverter.class)
	@Column(name = "schedule_type", nullable = false, length = 32)
	private PayrollScheduleType scheduleType;

	@Column(name = "day_of_month")
	private Short dayOfMonth;

	@Column(name = "trigger_delay_days", nullable = false)
	private Short triggerDelayDays;

	@Column(name = "is_active", nullable = false)
	private Boolean active;
}
