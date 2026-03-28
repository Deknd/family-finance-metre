package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.entity.enums.PayrollScheduleType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
class LastDayOfMonthPayrollEventCalculationStrategy extends AbstractPayrollEventCalculationStrategy {

	/**
	 * Возвращает тип правила для расчета выплаты в последний день месяца.
	 *
	 * @return тип правила `last_day_of_month`
	 */
	@Override
	public PayrollScheduleType supportedScheduleType() {
		return PayrollScheduleType.LAST_DAY_OF_MONTH;
	}

	@Override
	protected void validateSchedule(MemberPayrollScheduleEntity schedule) {
		if (schedule.getDayOfMonth() != null) {
			throw invalidSchedule("Payroll schedule dayOfMonth must be null for last day schedule");
		}
	}

	@Override
	protected LocalDate calculateNominalPayrollDate(MemberPayrollScheduleEntity schedule, YearMonth targetMonth) {
		return targetMonth.atEndOfMonth();
	}
}
