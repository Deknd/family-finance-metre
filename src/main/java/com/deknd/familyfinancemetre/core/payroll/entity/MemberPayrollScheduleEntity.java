package com.deknd.familyfinancemetre.core.payroll.entity;

import com.deknd.familyfinancemetre.core.common.entity.AbstractAuditableEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Сущность правила зарплатного события для конкретного члена семьи.
 */
@Getter
@Setter
@Entity
@Table(name = "member_payroll_schedules")
public class MemberPayrollScheduleEntity extends AbstractAuditableEntity {

	/**
	 * Пользователь, для которого настроено правило выплаты.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private FamilyMemberEntity member;

	/**
	 * Краткое название правила выплаты.
	 */
	@Column(name = "label")
	private String label;

	/**
	 * Тип расчета зарплатной даты.
	 */
	@Convert(converter = PayrollScheduleType.JpaConverter.class)
	@Column(name = "schedule_type", nullable = false, length = 32)
	private PayrollScheduleType scheduleType;

	/**
	 * День месяца для фиксированной даты выплаты.
	 */
	@Column(name = "day_of_month")
	private Short dayOfMonth;

	/**
	 * Количество дней между выплатой и запуском опроса.
	 */
	@Column(name = "trigger_delay_days", nullable = false)
	private Short triggerDelayDays;

	/**
	 * Признак участия правила в автоматическом запуске сбора.
	 */
	@Column(name = "is_active", nullable = false)
	private Boolean active;
}


