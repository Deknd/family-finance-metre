package com.deknd.familyfinancemetre.controller;

import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeAcceptedResponse;
import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeRequest;
import com.deknd.familyfinancemetre.service.IntakeSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intake")
public class UserFinanceIntakeController {

	private final IntakeSubmissionService intakeSubmissionService;

	public UserFinanceIntakeController(IntakeSubmissionService intakeSubmissionService) {
		this.intakeSubmissionService = intakeSubmissionService;
	}

	/**
	 * Принимает валидный intake payload от n8n и передает его в сервис обработки.
	 *
	 * @param request входной payload с финансовыми данными пользователя
	 * @return ответ о принятии payload в обработку
	 */
	@PostMapping("/user-finance-data")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public UserFinanceIntakeAcceptedResponse accept(@Valid @RequestBody UserFinanceIntakeRequest request) {
		return intakeSubmissionService.accept(request);
	}
}
