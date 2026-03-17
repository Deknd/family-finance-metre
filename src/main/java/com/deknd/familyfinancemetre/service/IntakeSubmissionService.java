package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeAcceptedResponse;
import com.deknd.familyfinancemetre.dto.intake.UserFinanceIntakeRequest;
import com.deknd.familyfinancemetre.dto.validation.ValidationErrorResponse.ValidationErrorDetail;
import com.deknd.familyfinancemetre.entity.FamilyEntity;
import com.deknd.familyfinancemetre.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.entity.FinanceSubmissionEntity;
import com.deknd.familyfinancemetre.entity.enums.SubmissionConfidence;
import com.deknd.familyfinancemetre.entity.enums.SubmissionSource;
import com.deknd.familyfinancemetre.exception.DuplicateSubmissionException;
import com.deknd.familyfinancemetre.exception.InvalidIntakePayloadReferenceException;
import com.deknd.familyfinancemetre.repository.FamilyMemberRepository;
import com.deknd.familyfinancemetre.repository.FamilyRepository;
import com.deknd.familyfinancemetre.repository.FinanceSubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class IntakeSubmissionService {

	private static final String DUPLICATE_SUBMISSION_CONSTRAINT = "uq_finance_submissions_external_submission_id";
	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

	private final FinanceSubmissionRepository financeSubmissionRepository;
	private final FamilyRepository familyRepository;
	private final FamilyMemberRepository familyMemberRepository;

	public IntakeSubmissionService(
		FinanceSubmissionRepository financeSubmissionRepository,
		FamilyRepository familyRepository,
		FamilyMemberRepository familyMemberRepository
	) {
		this.financeSubmissionRepository = financeSubmissionRepository;
		this.familyRepository = familyRepository;
		this.familyMemberRepository = familyMemberRepository;
	}

	/**
	 * Принимает валидный intake payload, сохраняет его в {@code finance_submissions}
	 * и защищает endpoint от повторной обработки одного и того же
	 * {@code external_submission_id}.
	 *
	 * @param request входной payload от n8n после завершения опроса пользователя
	 * @return ответ о принятии payload с идентификатором сохраненной submission
	 * @throws DuplicateSubmissionException если payload с таким external submission id уже был обработан
	 */
	@Transactional
	public UserFinanceIntakeAcceptedResponse accept(UserFinanceIntakeRequest request) {
		if (financeSubmissionRepository.existsByExternalSubmissionId(request.externalSubmissionId())) {
			throw new DuplicateSubmissionException();
		}

		ValidatedReferences validatedReferences = validateReferences(request);
		FinanceSubmissionEntity submission = buildSubmission(
			request,
			validatedReferences.family(),
			validatedReferences.member()
		);
		try {
			financeSubmissionRepository.saveAndFlush(submission);
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicateSubmissionViolation(exception)) {
				throw new DuplicateSubmissionException(exception);
			}
			throw exception;
		}

		return new UserFinanceIntakeAcceptedResponse(
			"accepted",
			submission.getId().toString(),
			request.familyId(),
			request.memberId(),
			true
		);
	}

	private ValidatedReferences validateReferences(UserFinanceIntakeRequest request) {
		UUID familyId = UUID.fromString(request.familyId());
		UUID memberId = UUID.fromString(request.memberId());
		Optional<FamilyEntity> family = familyRepository.findById(familyId);
		Optional<FamilyMemberEntity> member = familyMemberRepository.findById(memberId);
		List<ValidationErrorDetail> details = new ArrayList<>(2);

		if (family.isEmpty()) {
			details.add(new ValidationErrorDetail("family_id", "family does not exist"));
		}

		if (member.isEmpty()) {
			details.add(new ValidationErrorDetail("member_id", "member does not exist"));
		} else if (!familyId.equals(member.get().getFamily().getId())) {
			details.add(new ValidationErrorDetail("member_id", "member does not belong to the specified family"));
		}

		if (!details.isEmpty()) {
			throw new InvalidIntakePayloadReferenceException(details);
		}

		return new ValidatedReferences(family.orElseThrow(), member.orElseThrow());
	}

	private FinanceSubmissionEntity buildSubmission(
		UserFinanceIntakeRequest request,
		FamilyEntity family,
		FamilyMemberEntity member
	) {
		ObjectNode rawPayload = OBJECT_MAPPER.valueToTree(request);
		String requestId = normalizeOptionalRequestId(request.requestId());
		if (requestId == null) {
			rawPayload.remove("request_id");
		}

		FinanceSubmissionEntity submission = new FinanceSubmissionEntity();
		submission.setExternalSubmissionId(request.externalSubmissionId());
		submission.setRequestId(requestId);
		submission.setFamily(family);
		submission.setMember(member);
		submission.setSource(SubmissionSource.valueOf(request.source().toUpperCase(Locale.ROOT)));
		submission.setPeriodYear(request.period().year());
		submission.setPeriodMonth(request.period().month().shortValue());
		submission.setCollectedAt(OffsetDateTime.parse(request.collectedAt()));
		submission.setMonthlyIncome(request.financeInput().monthlyIncome());
		submission.setMonthlyExpenses(request.financeInput().monthlyExpenses());
		submission.setMonthlyCreditPayments(request.financeInput().monthlyCreditPayments());
		submission.setLiquidSavings(request.financeInput().liquidSavings());
		submission.setConfidence(resolveConfidence(request.meta().confidence()));
		submission.setNotes(request.meta().notes());
		submission.setRawPayload(rawPayload);
		return submission;
	}

	private String normalizeOptionalRequestId(String requestId) {
		if (requestId == null || requestId.isBlank()) {
			return null;
		}

		return requestId;
	}

	private SubmissionConfidence resolveConfidence(String confidence) {
		if (confidence == null || confidence.isBlank()) {
			return null;
		}

		return SubmissionConfidence.valueOf(confidence.toUpperCase(Locale.ROOT));
	}

	private boolean isDuplicateSubmissionViolation(DataIntegrityViolationException exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof ConstraintViolationException constraintViolationException
				&& DUPLICATE_SUBMISSION_CONSTRAINT.equalsIgnoreCase(constraintViolationException.getConstraintName())) {
				return true;
			}

			String message = current.getMessage();
			if (message != null) {
				String normalizedMessage = message.toLowerCase(Locale.ROOT);
				if (normalizedMessage.contains(DUPLICATE_SUBMISSION_CONSTRAINT)
					|| normalizedMessage.contains("external_submission_id")) {
					return true;
				}
			}

			current = current.getCause();
		}

		return false;
	}

	private record ValidatedReferences(
		FamilyEntity family,
		FamilyMemberEntity member
	) {
	}
}
