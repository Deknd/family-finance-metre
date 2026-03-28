package com.deknd.familyfinancemetre.flow.intake.web;

import com.deknd.familyfinancemetre.flow.intake.dto.UserFinanceIntakeAcceptedResponse;
import com.deknd.familyfinancemetre.flow.intake.dto.UserFinanceIntakeRequest;
import com.deknd.familyfinancemetre.flow.intake.service.UserFinanceIntakeOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/intake")
public class UserFinanceIntakeController {

	private final UserFinanceIntakeOrchestrationService userFinanceIntakeOrchestrationService;

	/**
	 * Принимает валидный intake payload от n8n и передает его в сервис обработки.
	 *
	 * @param request входной payload с финансовыми данными пользователя
	 * @return ответ о принятии payload в обработку
	 */
	@PostMapping("/user-finance-data")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public UserFinanceIntakeAcceptedResponse accept(@Valid @RequestBody UserFinanceIntakeRequest request) {
		return userFinanceIntakeOrchestrationService.accept(request);
	}
}


