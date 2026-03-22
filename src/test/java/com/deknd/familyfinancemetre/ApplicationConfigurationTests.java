package com.deknd.familyfinancemetre;

import com.deknd.familyfinancemetre.config.ApplicationProperties;
import com.deknd.familyfinancemetre.config.DatabaseProperties;
import com.deknd.familyfinancemetre.service.DeviceDashboardReadService;
import com.deknd.familyfinancemetre.service.FamilyDashboardSnapshotRecalculationService;
import com.deknd.familyfinancemetre.service.IntakeSubmissionService;
import com.deknd.familyfinancemetre.service.MemberFinanceSnapshotRecalculationService;
import com.deknd.familyfinancemetre.service.UserFinanceIntakeOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationConfigurationTests {

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

	@Autowired
	private DatabaseProperties databaseProperties;

	@Autowired
	private ApplicationProperties applicationProperties;

	@Autowired
	private Clock clock;

	@Autowired
	private Environment environment;

	@Test
	void configurationPropertiesAreBoundFromTestProfile() {
		assertThat(databaseProperties.url()).isEqualTo("jdbc:postgresql://localhost:15432/family_finance_metre_test");
		assertThat(databaseProperties.username()).isEqualTo("test_user");
		assertThat(databaseProperties.password()).isEqualTo("test_password");
		assertThat(databaseProperties.driverClassName()).isEqualTo("org.postgresql.Driver");

		assertThat(applicationProperties.timezone()).isEqualTo(ZoneId.of("Europe/Moscow"));
		assertThat(applicationProperties.security().n8nApiKey()).isEqualTo("test-n8n-api-key");
		assertThat(applicationProperties.integrations().n8n().webhookUrl().toString())
			.isEqualTo("https://n8n.test.local/webhook/finance-intake-start");
		assertThat(applicationProperties.integrations().n8n().bearerToken()).isEqualTo("test-n8n-webhook-token");
		assertThat(applicationProperties.integrations().n8n().callbackSubmitUrl().toString())
			.isEqualTo("https://server.test.local/api/v1/intake/user-finance-data");
		assertThat(applicationProperties.integrations().n8n().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
		assertThat(applicationProperties.integrations().n8n().readTimeout()).isEqualTo(Duration.ofSeconds(10));
		assertThat(applicationProperties.scheduler().payrollCollection().enabled()).isFalse();
		assertThat(applicationProperties.scheduler().payrollCollection().cron()).isEqualTo("0 0 9 * * *");
	}

	@Test
	void testProfileProvidesClockAndProfileSpecificConfiguration() {
		assertThat(environment.getActiveProfiles()).containsExactly("test");
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
	}

}
