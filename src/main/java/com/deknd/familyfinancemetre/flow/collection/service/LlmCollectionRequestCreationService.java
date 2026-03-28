package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestReason;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyMemberEntity;
import com.deknd.familyfinancemetre.core.payroll.entity.MemberPayrollScheduleEntity;
import com.deknd.familyfinancemetre.core.payroll.service.PayrollEventCalculationResult;
import com.deknd.familyfinancemetre.shared.config.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Создает трассируемую запись запуска сбора данных перед вызовом n8n.
 */
@Service
@RequiredArgsConstructor
public class LlmCollectionRequestCreationService {

	private static final String DUPLICATE_PAYROLL_EVENT_CONSTRAINT = "uq_llm_requests_schedule_effective_date";
	private static final String COLLECTION_REASON = "day_after_salary";
	private static final String COLLECTION_LOCALE = "ru-RU";
	private static final boolean ALLOW_APPROXIMATE_VALUES = true;
	private static final int MAX_CLARIFYING_QUESTIONS_PER_FIELD = 1;
	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

	private final LlmCollectionRequestRepository llmCollectionRequestRepository;
	private final ApplicationProperties applicationProperties;
	private final Clock clock;

	/**
	 * Создает pending-запрос на сбор данных по payroll-событию.
	 *
	 * @param payrollSchedule правило выплаты, по которому запускается сбор
	 * @param payrollEvent рассчитанные даты payroll-события и целевой период
	 * @return сохраненная запись, если запрос создан впервые, иначе пустой результат для already-created события
	 */
	@Transactional
	public Optional<LlmCollectionRequestEntity> createPendingRequest(
		MemberPayrollScheduleEntity payrollSchedule,
		PayrollEventCalculationResult payrollEvent
	) {
		Objects.requireNonNull(payrollSchedule, "Payroll schedule must not be null");
		Objects.requireNonNull(payrollEvent, "Payroll event must not be null");

		Optional<LlmCollectionRequestEntity> existingRequest = findExistingRequest(payrollSchedule, payrollEvent);
		if (existingRequest.isPresent()) {
			return Optional.empty();
		}

		LlmCollectionRequestEntity request = buildRequest(payrollSchedule, payrollEvent);
		try {
			return Optional.of(llmCollectionRequestRepository.saveAndFlush(request));
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicatePayrollEventViolation(exception)) {
				return Optional.empty();
			}
			throw exception;
		}
	}

	private Optional<LlmCollectionRequestEntity> findExistingRequest(
		MemberPayrollScheduleEntity payrollSchedule,
		PayrollEventCalculationResult payrollEvent
	) {
		UUID payrollScheduleId = Objects.requireNonNull(payrollSchedule.getId(), "Payroll schedule id must not be null");
		return llmCollectionRequestRepository.findByPayrollScheduleIdAndEffectivePayrollDate(
			payrollScheduleId,
			payrollEvent.effectivePayrollDate()
		);
	}

	private LlmCollectionRequestEntity buildRequest(
		MemberPayrollScheduleEntity payrollSchedule,
		PayrollEventCalculationResult payrollEvent
	) {
		FamilyMemberEntity member = Objects.requireNonNull(payrollSchedule.getMember(), "Payroll schedule member must not be null");
		FamilyEntity family = Objects.requireNonNull(member.getFamily(), "Payroll schedule family must not be null");
		OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
		String requestId = UUID.randomUUID().toString();
		ArrayNode requestedFields = buildRequestedFields();
		ObjectNode requestPayload = buildRequestPayload(
			requestId,
			triggeredAt,
			family,
			member,
			payrollSchedule,
			payrollEvent,
			requestedFields
		);

		LlmCollectionRequestEntity request = new LlmCollectionRequestEntity();
		request.setRequestId(requestId);
		request.setFamily(family);
		request.setMember(member);
		request.setPayrollSchedule(payrollSchedule);
		request.setPeriodYear(payrollEvent.periodYear());
		request.setPeriodMonth(payrollEvent.periodMonth());
		request.setReason(LlmCollectionRequestReason.DAY_AFTER_SALARY);
		request.setStatus(LlmCollectionRequestStatus.PENDING);
		request.setRequestedFields(requestedFields);
		request.setNominalPayrollDate(payrollEvent.nominalPayrollDate());
		request.setEffectivePayrollDate(payrollEvent.effectivePayrollDate());
		request.setScheduledTriggerDate(payrollEvent.scheduledTriggerDate());
		request.setTriggeredAt(triggeredAt);
		request.setRequestPayload(requestPayload);
		return request;
	}

	private ArrayNode buildRequestedFields() {
		return OBJECT_MAPPER.createArrayNode()
			.add("monthly_income")
			.add("monthly_expenses")
			.add("monthly_credit_payments")
			.add("liquid_savings");
	}

	private ObjectNode buildRequestPayload(
		String requestId,
		OffsetDateTime triggeredAt,
		FamilyEntity family,
		FamilyMemberEntity member,
		MemberPayrollScheduleEntity payrollSchedule,
		PayrollEventCalculationResult payrollEvent,
		ArrayNode requestedFields
	) {
		ObjectNode payload = OBJECT_MAPPER.createObjectNode();
		payload.put("request_id", requestId);
		payload.put("triggered_at", triggeredAt.toString());
		payload.put("reason", COLLECTION_REASON);
		payload.set("family", buildFamilyPayload(family));
		payload.set("member", buildMemberPayload(member));
		payload.set("payroll_event", buildPayrollEventPayload(payrollSchedule, payrollEvent));
		payload.set("collection_scope", buildCollectionScopePayload(payrollEvent, requestedFields));
		payload.set("instructions", buildInstructionsPayload(family));
		payload.set("callback", buildCallbackPayload());
		return payload;
	}

	private ObjectNode buildFamilyPayload(FamilyEntity family) {
		ObjectNode familyPayload = OBJECT_MAPPER.createObjectNode();
		familyPayload.put("id", family.getId().toString());
		putNullableText(familyPayload, "name", family.getName());
		return familyPayload;
	}

	private ObjectNode buildMemberPayload(FamilyMemberEntity member) {
		ObjectNode memberPayload = OBJECT_MAPPER.createObjectNode();
		memberPayload.put("id", member.getId().toString());
		putNullableText(memberPayload, "name", resolveMemberName(member));
		putNullableText(memberPayload, "telegram_chat_id", member.getTelegramChatId());
		return memberPayload;
	}

	private ObjectNode buildPayrollEventPayload(
		MemberPayrollScheduleEntity payrollSchedule,
		PayrollEventCalculationResult payrollEvent
	) {
		ObjectNode payrollEventPayload = OBJECT_MAPPER.createObjectNode();
		payrollEventPayload.put("schedule_id", payrollSchedule.getId().toString());
		payrollEventPayload.put("schedule_type", payrollSchedule.getScheduleType().getDatabaseValue());
		if (payrollSchedule.getDayOfMonth() == null) {
			payrollEventPayload.putNull("day_of_month");
		} else {
			payrollEventPayload.put("day_of_month", payrollSchedule.getDayOfMonth());
		}
		payrollEventPayload.put("nominal_payroll_date", payrollEvent.nominalPayrollDate().toString());
		payrollEventPayload.put("effective_payroll_date", payrollEvent.effectivePayrollDate().toString());
		payrollEventPayload.put("trigger_delay_days", payrollSchedule.getTriggerDelayDays());
		payrollEventPayload.put("scheduled_trigger_date", payrollEvent.scheduledTriggerDate().toString());
		return payrollEventPayload;
	}

	private ObjectNode buildCollectionScopePayload(
		PayrollEventCalculationResult payrollEvent,
		ArrayNode requestedFields
	) {
		ObjectNode collectionScopePayload = OBJECT_MAPPER.createObjectNode();
		collectionScopePayload.put("period_year", payrollEvent.periodYear());
		collectionScopePayload.put("period_month", payrollEvent.periodMonth());
		collectionScopePayload.set("fields", requestedFields.deepCopy());
		return collectionScopePayload;
	}

	private ObjectNode buildInstructionsPayload(FamilyEntity family) {
		ObjectNode instructionsPayload = OBJECT_MAPPER.createObjectNode();
		instructionsPayload.put("locale", COLLECTION_LOCALE);
		instructionsPayload.put("currency", family.getCurrencyCode());
		instructionsPayload.put("allow_approximate_values", ALLOW_APPROXIMATE_VALUES);
		instructionsPayload.put("max_clarifying_questions_per_field", MAX_CLARIFYING_QUESTIONS_PER_FIELD);
		return instructionsPayload;
	}

	private ObjectNode buildCallbackPayload() {
		ObjectNode callbackPayload = OBJECT_MAPPER.createObjectNode();
		callbackPayload.put("submit_url", applicationProperties.integrations().n8n().callbackSubmitUrl().toString());
		return callbackPayload;
	}

	private String resolveMemberName(FamilyMemberEntity member) {
		if (member.getDisplayName() != null && !member.getDisplayName().isBlank()) {
			return member.getDisplayName();
		}
		return member.getFirstName();
	}

	private void putNullableText(ObjectNode node, String fieldName, String value) {
		if (value == null) {
			node.putNull(fieldName);
			return;
		}
		node.put(fieldName, value);
	}

	private boolean isDuplicatePayrollEventViolation(DataIntegrityViolationException exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof ConstraintViolationException constraintViolationException
				&& DUPLICATE_PAYROLL_EVENT_CONSTRAINT.equalsIgnoreCase(constraintViolationException.getConstraintName())) {
				return true;
			}

			String message = current.getMessage();
			if (message != null) {
				String normalizedMessage = message.toLowerCase(Locale.ROOT);
				if (normalizedMessage.contains(DUPLICATE_PAYROLL_EVENT_CONSTRAINT)
					|| normalizedMessage.contains("payroll_schedule_id")
					|| normalizedMessage.contains("effective_payroll_date")) {
					return true;
				}
			}

			current = current.getCause();
		}

		return false;
	}
}
