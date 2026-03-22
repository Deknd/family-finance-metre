package com.deknd.familyfinancemetre;

import com.deknd.familyfinancemetre.service.IntakeSubmissionService;
import com.deknd.familyfinancemetre.service.FamilyDashboardSnapshotRecalculationService;
import com.deknd.familyfinancemetre.service.MemberFinanceSnapshotRecalculationService;
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
