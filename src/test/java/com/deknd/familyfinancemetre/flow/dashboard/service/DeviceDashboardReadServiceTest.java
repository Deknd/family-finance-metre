package com.deknd.familyfinancemetre.flow.dashboard.service;

import com.deknd.familyfinancemetre.flow.dashboard.dto.DeviceDashboardResponse;
import com.deknd.familyfinancemetre.core.device.entity.DeviceEntity;
import com.deknd.familyfinancemetre.core.snapshot.entity.FamilyDashboardSnapshotEntity;
import com.deknd.familyfinancemetre.core.family.entity.FamilyEntity;
import com.deknd.familyfinancemetre.core.snapshot.enums.DashboardStatus;
import com.deknd.familyfinancemetre.flow.dashboard.exception.DashboardNotReadyException;
import com.deknd.familyfinancemetre.core.device.repository.DeviceRepository;
import com.deknd.familyfinancemetre.core.snapshot.repository.FamilyDashboardSnapshotRepository;
import com.deknd.familyfinancemetre.shared.security.principal.DevicePrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeviceDashboardReadServiceTest {

	private static final UUID DEVICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Clock FIXED_CLOCK = Clock.fixed(
		OffsetDateTime.parse("2026-03-22T07:20:00Z").toInstant(),
		ZoneOffset.UTC
	);

	@Mock
	private FamilyDashboardSnapshotRepository familyDashboardSnapshotRepository;

	@Mock
	private DeviceRepository deviceRepository;

	@Mock
	private Clock clock;

	@InjectMocks
	private DeviceDashboardReadService deviceDashboardReadService;

	@Test
	@DisplayName("Возвращает готовый payload dashboard и обновляет время последнего успешного запроса устройства")
	void readDashboardReturnsMappedDashboardPayloadAndUpdatesDeviceLastSeenAt() {
		DevicePrincipal devicePrincipal = new DevicePrincipal(DEVICE_ID, FAMILY_ID, "Kitchen display");
		DeviceEntity device = new DeviceEntity();
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
		given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.of(device));
		given(clock.instant()).willReturn(FIXED_CLOCK.instant());
		given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());

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
		assertThat(device.getLastSeenAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
	}

	@Test
	@DisplayName("Не обновляет last seen и бросает доменную ошибку, если для семьи еще нет dashboard snapshot")
	void readDashboardThrowsWhenFamilySnapshotDoesNotExist() {
		DevicePrincipal devicePrincipal = new DevicePrincipal(DEVICE_ID, FAMILY_ID, "Kitchen display");

		given(familyDashboardSnapshotRepository.findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(FAMILY_ID))
			.willReturn(Optional.empty());

		assertThatThrownBy(() -> deviceDashboardReadService.readDashboard(devicePrincipal))
			.isInstanceOf(DashboardNotReadyException.class)
			.hasMessage(DashboardNotReadyException.ERROR_MESSAGE);

		verifyNoInteractions(deviceRepository, clock);
	}

	@Test
	@DisplayName("Бросает ошибку, если аутентифицированное устройство отсутствует в базе")
	void readDashboardThrowsWhenAuthenticatedDeviceDoesNotExist() {
		DevicePrincipal devicePrincipal = new DevicePrincipal(DEVICE_ID, FAMILY_ID, "Kitchen display");
		FamilyDashboardSnapshotEntity snapshot = new FamilyDashboardSnapshotEntity();
		snapshot.setFamily(family("Europe/Moscow", "RUB"));
		snapshot.setStatus(DashboardStatus.NORMAL);
		snapshot.setStatusText("Норма");
		snapshot.setStatusReason("Показатели в пределах нормы");
		snapshot.setMonthlyIncome(100000);
		snapshot.setMonthlyExpenses(50000);
		snapshot.setCreditLoadPercent(new BigDecimal("10.00"));
		snapshot.setEmergencyFundMonths(new BigDecimal("4.00"));
		snapshot.setCalculatedAt(OffsetDateTime.parse("2026-03-22T07:15:30Z"));

		given(familyDashboardSnapshotRepository.findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(FAMILY_ID))
			.willReturn(Optional.of(snapshot));
		given(deviceRepository.findById(DEVICE_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> deviceDashboardReadService.readDashboard(devicePrincipal))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Authenticated device is missing: " + DEVICE_ID);

		verify(deviceRepository).findById(DEVICE_ID);
		verifyNoInteractions(clock);
	}

	private FamilyEntity family(String timezone, String currencyCode) {
		FamilyEntity family = new FamilyEntity();
		family.setId(FAMILY_ID);
		family.setTimezone(timezone);
		family.setCurrencyCode(currencyCode);
		return family;
	}
}


