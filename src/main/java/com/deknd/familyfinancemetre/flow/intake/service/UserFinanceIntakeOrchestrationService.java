package com.deknd.familyfinancemetre.flow.intake.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.deknd.familyfinancemetre.core.snapshot.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.core.snapshot.service.FamilyDashboardSnapshotRecalculationService;
import com.deknd.familyfinancemetre.core.snapshot.service.MemberFinanceSnapshotRecalculationService;
import com.deknd.familyfinancemetre.flow.intake.dto.UserFinanceIntakeAcceptedResponse;
import com.deknd.familyfinancemetre.flow.intake.dto.UserFinanceIntakeRequest;
import com.deknd.familyfinancemetre.flow.intake.exception.InvalidIntakePayloadReferenceException;
import com.deknd.familyfinancemetre.shared.web.error.ValidationErrorResponse.ValidationErrorDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserFinanceIntakeOrchestrationService {

	private final IntakeSubmissionService intakeSubmissionService;
	private final MemberFinanceSnapshotRecalculationService memberFinanceSnapshotRecalculationService;
	private final FamilyDashboardSnapshotRecalculationService familyDashboardSnapshotRecalculationService;
	private final LlmCollectionRequestRepository llmCollectionRequestRepository;
	private final Clock clock;

	/**
	 * Выполняет полный intake-поток: связывает callback с запросом агента,
	 * сохраняет submission и пересчитывает производные snapshot-таблицы в одной транзакции.
	 *
	 * @param request входной payload от n8n после завершения опроса пользователя
	 * @return ответ о принятии payload с идентификатором сохраненной submission
	 */
	@Transactional
	public UserFinanceIntakeAcceptedResponse accept(UserFinanceIntakeRequest request) {
		String normalizedRequestId = normalizeOptionalRequestId(request.requestId());
		LlmCollectionRequestEntity llmCollectionRequest = resolveLlmCollectionRequest(normalizedRequestId);
		FinanceSubmissionEntity submission = intakeSubmissionService.saveSubmission(request, llmCollectionRequest);

		memberFinanceSnapshotRecalculationService.recalculateForMemberPeriod(
			submission.getMember().getId(),
			submission.getPeriodYear(),
			submission.getPeriodMonth()
		);
		familyDashboardSnapshotRecalculationService.recalculateForFamilyPeriod(
			submission.getFamily().getId(),
			submission.getPeriodYear(),
			submission.getPeriodMonth()
		);

		if (llmCollectionRequest != null) {
			llmCollectionRequest.setStatus(LlmCollectionRequestStatus.COMPLETED);
			llmCollectionRequest.setCompletedAt(OffsetDateTime.now(clock));
		}

		return new UserFinanceIntakeAcceptedResponse(
			"accepted",
			submission.getId().toString(),
			submission.getFamily().getId().toString(),
			submission.getMember().getId().toString(),
			true
		);
	}

	private LlmCollectionRequestEntity resolveLlmCollectionRequest(String requestId) {
		if (requestId == null) {
			return null;
		}

		return llmCollectionRequestRepository.findByRequestId(requestId)
			.orElseThrow(() -> new InvalidIntakePayloadReferenceException(List.of(
				new ValidationErrorDetail("request_id", "llm collection request does not exist")
			)));
	}

	private String normalizeOptionalRequestId(String requestId) {
		if (requestId == null || requestId.isBlank()) {
			return null;
		}

		return requestId;
	}
}


