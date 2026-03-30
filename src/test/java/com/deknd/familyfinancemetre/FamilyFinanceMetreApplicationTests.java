package com.deknd.familyfinancemetre;

import com.deknd.familyfinancemetre.core.collection.repository.LlmCollectionRequestRepository;
import com.deknd.familyfinancemetre.flow.collection.service.PayrollCollectionOrchestrationService;
import com.deknd.familyfinancemetre.flow.dashboard.service.DeviceDashboardReadService;
import com.deknd.familyfinancemetre.flow.intake.service.IntakeSubmissionService;
import com.deknd.familyfinancemetre.core.snapshot.service.FamilyDashboardSnapshotRecalculationService;
import com.deknd.familyfinancemetre.core.snapshot.service.MemberFinanceSnapshotRecalculationService;
import com.deknd.familyfinancemetre.flow.intake.service.UserFinanceIntakeOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class FamilyFinanceMetreApplicationTests {

	@MockitoBean
	private IntakeSubmissionService intakeSubmissionService;

	@MockitoBean
	private MemberFinanceSnapshotRecalculationService memberFinanceSnapshotRecalculationService;

	@MockitoBean
	private FamilyDashboardSnapshotRecalculationService familyDashboardSnapshotRecalculationService;

	@MockitoBean
	private UserFinanceIntakeOrchestrationService userFinanceIntakeOrchestrationService;

	@MockitoBean
	private DeviceDashboardReadService deviceDashboardReadService;

	@MockitoBean
	private LlmCollectionRequestRepository llmCollectionRequestRepository;

	@MockitoBean
	private PayrollCollectionOrchestrationService payrollCollectionOrchestrationService;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointReturnsUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

}

