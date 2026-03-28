package com.deknd.familyfinancemetre.core.payroll.service;

import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.enums.PayrollScheduleType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
class FixedDayOfMonthPayrollEventCalculationStrategy extends AbstractPayrollEventCalculationStrategy {

	/**
	 * Возвращает тип правила для расчета выплаты по фиксированному дню месяца.
	 *
	 * @return тип правила `fixed_day_of_month`
	 */
	@Override
	public PayrollScheduleType supportedScheduleType() {
		return PayrollScheduleType.FIXED_DAY_OF_MONTH;
	}

	@Override
	protected void validateSchedule(MemberPayrollScheduleEntity schedule) {
		Short dayOfMonth = schedule.getDayOfMonth();
		if (dayOfMonth == null) {
			throw invalidSchedule("Payroll schedule dayOfMonth must not be null for fixed day schedule");
		}
		if (dayOfMonth < 1 || dayOfMonth > 31) {
			throw invalidSchedule("Payroll schedule dayOfMonth must be between 1 and 31 for fixed day schedule");
		}
	}

	@Override
	protected LocalDate calculateNominalPayrollDate(MemberPayrollScheduleEntity schedule, YearMonth targetMonth) {
		int clampedDayOfMonth = Math.min(schedule.getDayOfMonth(), targetMonth.lengthOfMonth());
		return targetMonth.atDay(clampedDayOfMonth);
	}
}


