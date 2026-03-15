package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeAcceptedResponse;
import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeRequest;
import org.springframework.stereotype.Service;

@Service
public class IntakeSubmissionService {

	public UserFinanceIntakeAcceptedResponse accept(UserFinanceIntakeRequest request) {
		return new UserFinanceIntakeAcceptedResponse(
			"accepted",
			"subm_" + java.util.UUID.randomUUID(),
			request.familyId(),
			request.memberId(),
			true
		);
	}
}
