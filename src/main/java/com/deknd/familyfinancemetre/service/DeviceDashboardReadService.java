package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.dto.device.DeviceDashboardResponse;
import com.deknd.familyfinancemetre.entity.DeviceEntity;
import com.deknd.familyfinancemetre.entity.FamilyDashboardSnapshotEntity;
import com.deknd.familyfinancemetre.exception.DashboardNotReadyException;
import com.deknd.familyfinancemetre.repository.DeviceRepository;
import com.deknd.familyfinancemetre.repository.FamilyDashboardSnapshotRepository;
import com.deknd.familyfinancemetre.security.principal.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceDashboardReadService {

	private static final DateTimeFormatter UPDATED_AT_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");

	private final FamilyDashboardSnapshotRepository familyDashboardSnapshotRepository;
	private final DeviceRepository deviceRepository;
	private final Clock clock;

	/**
	 * Возвращает готовый dashboard для физического устройства по данным уже аутентифицированного устройства.
	 *
	 * @param devicePrincipal аутентифицированное устройство с идентификатором семьи
	 * @return готовый payload dashboard для экрана устройства
	 * @throws DashboardNotReadyException если для семьи еще не рассчитан ни один dashboard snapshot
	 */
	@Transactional
	public DeviceDashboardResponse readDashboard(DevicePrincipal devicePrincipal) {
		FamilyDashboardSnapshotEntity snapshot = familyDashboardSnapshotRepository
			.findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(devicePrincipal.familyId())
			.orElseThrow(DashboardNotReadyException::new);
		DeviceEntity device = findDevice(devicePrincipal.deviceId());

		ZoneId familyZoneId = ZoneId.of(snapshot.getFamily().getTimezone());
		OffsetDateTime generatedAt = snapshot.getCalculatedAt()
			.atZoneSameInstant(familyZoneId)
			.toOffsetDateTime();
		device.setLastSeenAt(OffsetDateTime.now(clock));

		return new DeviceDashboardResponse(
			generatedAt,
			devicePrincipal.deviceId().toString(),
			devicePrincipal.familyId().toString(),
			snapshot.getStatus().getDatabaseValue(),
			snapshot.getStatusText(),
			snapshot.getStatusReason(),
			new DeviceDashboardResponse.Metrics(
				snapshot.getMonthlyIncome(),
				snapshot.getMonthlyExpenses(),
				snapshot.getCreditLoadPercent(),
				snapshot.getEmergencyFundMonths()
			),
			new DeviceDashboardResponse.Display(
				snapshot.getFamily().getCurrencyCode(),
				generatedAt.format(UPDATED_AT_LABEL_FORMATTER)
			)
		);
	}

	private DeviceEntity findDevice(UUID deviceId) {
		return deviceRepository.findById(deviceId)
			.orElseThrow(() -> new IllegalStateException("Authenticated device is missing: " + deviceId));
	}
}
