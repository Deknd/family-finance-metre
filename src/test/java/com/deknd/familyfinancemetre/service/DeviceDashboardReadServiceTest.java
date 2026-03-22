package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.dto.device.DeviceDashboardResponse;
import com.deknd.familyfinancemetre.entity.FamilyDashboardSnapshotEntity;
import com.deknd.familyfinancemetre.entity.FamilyEntity;
import com.deknd.familyfinancemetre.entity.enums.DashboardStatus;
import com.deknd.familyfinancemetre.exception.DashboardNotReadyException;
import com.deknd.familyfinancemetre.repository.FamilyDashboardSnapshotRepository;
import com.deknd.familyfinancemetre.security.principal.DevicePrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeviceDashboardReadServiceTest {

	private static final UUID DEVICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private FamilyDashboardSnapshotRepository familyDashboardSnapshotRepository;

	@InjectMocks
	private DeviceDashboardReadService deviceDashboardReadService;

	@Test
	@DisplayName("Возвращает готовый payload dashboard для аутентифицированного устройства")
	void readDashboardReturnsMappedDashboardPayload() {
		DevicePrincipal devicePrincipal = new DevicePrincipal(DEVICE_ID, FAMILY_ID, "Kitchen display");
		FamilyDashboardSnapshotEntity snapshot = new FamilyDashboardSnapshotEntity();
		snapshot.setFamily(family("Asia/Yekaterinburg", "RUB"));
		snapshot.setStatus(DashboardStatus.WARNING);
		snapshot.setStatusText("Внимание");
		snapshot.setStatusReason("Подушка ниже комфортной зоны");
		snapshot.setMonthlyIncome(210000);
		snapshot.setMonthlyExpenses(90000);
		snapshot.setCreditLoadPercent(new BigDecimal("27.00"));
		snapshot.setEmergencyFundMonths(new BigDecimal("2.00"));
		snapshot.setCalculatedAt(OffsetDateTime.parse("2026-03-22T07:15:30Z"));

		given(familyDashboardSnapshotRepository.findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(FAMILY_ID))
			.willReturn(Optional.of(snapshot));

		DeviceDashboardResponse response = deviceDashboardReadService.readDashboard(devicePrincipal);

		assertThat(response.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-22T12:15:30+05:00"));
		assertThat(response.deviceId()).isEqualTo(DEVICE_ID.toString());
		assertThat(response.familyId()).isEqualTo(FAMILY_ID.toString());
		assertThat(response.status()).isEqualTo("warning");
		assertThat(response.statusText()).isEqualTo("Внимание");
		assertThat(response.statusReason()).isEqualTo("Подушка ниже комфортной зоны");
		assertThat(response.metrics()).isEqualTo(new DeviceDashboardResponse.Metrics(
			210000,
			90000,
			new BigDecimal("27.00"),
			new BigDecimal("2.00")
		));
		assertThat(response.display()).isEqualTo(new DeviceDashboardResponse.Display("RUB", "22.03 12:15"));
	}

	@Test
	@DisplayName("Бросает доменную ошибку, если для семьи еще нет dashboard snapshot")
	void readDashboardThrowsWhenFamilySnapshotDoesNotExist() {
		DevicePrincipal devicePrincipal = new DevicePrincipal(DEVICE_ID, FAMILY_ID, "Kitchen display");

		given(familyDashboardSnapshotRepository.findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(FAMILY_ID))
			.willReturn(Optional.empty());

		assertThatThrownBy(() -> deviceDashboardReadService.readDashboard(devicePrincipal))
			.isInstanceOf(DashboardNotReadyException.class)
			.hasMessage(DashboardNotReadyException.ERROR_MESSAGE);
	}

	private FamilyEntity family(String timezone, String currencyCode) {
		FamilyEntity family = new FamilyEntity();
		family.setId(FAMILY_ID);
		family.setTimezone(timezone);
		family.setCurrencyCode(currencyCode);
		return family;
	}
}
