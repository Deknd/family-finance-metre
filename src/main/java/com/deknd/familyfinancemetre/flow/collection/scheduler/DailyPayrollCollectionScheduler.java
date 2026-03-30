package com.deknd.familyfinancemetre.flow.collection.scheduler;

import com.deknd.familyfinancemetre.flow.collection.service.PayrollCollectionOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Тонкий scheduler-адаптер прикладного потока {@code server -> n8n}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduler.payroll-collection", name = "enabled", havingValue = "true")
public class DailyPayrollCollectionScheduler {

	private final PayrollCollectionOrchestrationService payrollCollectionOrchestrationService;

	/**
	 * Запускает ежедневную orchestration-джобу payroll collection по cron-конфигурации приложения.
	 */
	@Scheduled(cron = "${app.scheduler.payroll-collection.cron}", zone = "${app.timezone}")
	public void runDailyPayrollCollection() {
		payrollCollectionOrchestrationService.runDailyPayrollCollection();
	}
}
