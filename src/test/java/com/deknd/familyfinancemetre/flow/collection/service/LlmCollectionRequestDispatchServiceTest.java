package com.deknd.familyfinancemetre.flow.collection.service;

import com.deknd.familyfinancemetre.core.collection.entity.LlmCollectionRequestEntity;
import com.deknd.familyfinancemetre.core.collection.enums.LlmCollectionRequestStatus;
import com.deknd.familyfinancemetre.core.collection.service.LlmCollectionRequestLifecycleService;
import com.deknd.familyfinancemetre.flow.collection.client.N8nClient;
import com.deknd.familyfinancemetre.flow.collection.client.N8nStartCollectionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LlmCollectionRequestDispatchServiceTest {

	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

	@Mock
	private N8nClient n8nClient;

	@Mock
	private LlmCollectionRequestLifecycleService llmCollectionRequestLifecycleService;

	@Test
	@DisplayName("При accepted ответе n8n отправляет payload и делегирует переход в accepted lifecycle сервису")
	void dispatchPendingRequestMarksAcceptedWhenN8nReturnsAccepted() throws Exception {
		LlmCollectionRequestDispatchService dispatchService = new LlmCollectionRequestDispatchService(
			n8nClient,
			llmCollectionRequestLifecycleService
		);
		LlmCollectionRequestEntity request = pendingRequest();
		N8nStartCollectionResult.Accepted acceptedResult = new N8nStartCollectionResult.Accepted(
			"accepted",
			"99999999-9999-9999-9999-999999999999",
			"n8n-run-001",
			OBJECT_MAPPER.readTree("""
				{
				  "status": "accepted",
				  "request_id": "99999999-9999-9999-9999-999999999999",
				  "workflow_run_id": "n8n-run-001"
				}
				""")
		);
		given(n8nClient.startCollection(request.getRequestId(), request.getRequestPayload())).willReturn(acceptedResult);

		N8nStartCollectionResult result = dispatchService.dispatchPendingRequest(request);

		assertThat(result).isSameAs(acceptedResult);
		verify(n8nClient).startCollection(request.getRequestId(), request.getRequestPayload());
		verify(llmCollectionRequestLifecycleService).markAccepted(
			request,
			"n8n-run-001",
			acceptedResult.responsePayload()
		);
	}

	@Test
	@DisplayName("При failed результате n8n делегирует переход в failed lifecycle сервису")
	void dispatchPendingRequestMarksFailedWhenN8nReturnsFailure() throws Exception {
		LlmCollectionRequestDispatchService dispatchService = new LlmCollectionRequestDispatchService(
			n8nClient,
			llmCollectionRequestLifecycleService
		);
		LlmCollectionRequestEntity request = pendingRequest();
		N8nStartCollectionResult.Failed failedResult = new N8nStartCollectionResult.Failed(
			401,
			OBJECT_MAPPER.readTree("""
				{
				  "error": {
				    "code": "INVALID_TOKEN"
				  }
				}
				"""),
			"n8n вернул HTTP 401: Authorization shared secret is invalid"
		);
		given(n8nClient.startCollection(request.getRequestId(), request.getRequestPayload())).willReturn(failedResult);

		N8nStartCollectionResult result = dispatchService.dispatchPendingRequest(request);

		assertThat(result).isSameAs(failedResult);
		verify(n8nClient).startCollection(request.getRequestId(), request.getRequestPayload());
		verify(llmCollectionRequestLifecycleService).markFailed(
			request,
			failedResult.responsePayload(),
			failedResult.errorMessage()
		);
	}

	private LlmCollectionRequestEntity pendingRequest() throws Exception {
		LlmCollectionRequestEntity request = new LlmCollectionRequestEntity();
		request.setRequestId("99999999-9999-9999-9999-999999999999");
		request.setStatus(LlmCollectionRequestStatus.PENDING);
		request.setRequestPayload(OBJECT_MAPPER.readTree("""
			{
			  "request_id": "99999999-9999-9999-9999-999999999999",
			  "member": {
			    "id": "22222222-2222-2222-2222-222222222222"
			  }
			}
			"""));
		return request;
	}
}
