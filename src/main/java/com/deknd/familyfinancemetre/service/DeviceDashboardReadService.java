package com.deknd.familyfinancemetre.service;

import com.deknd.familyfinancemetre.dto.device.DeviceDashboardResponse;
import com.deknd.familyfinancemetre.entity.FamilyDashboardSnapshotEntity;
import com.deknd.familyfinancemetre.exception.DashboardNotReadyException;
import com.deknd.familyfinancemetre.repository.FamilyDashboardSnapshotRepository;
import com.deknd.familyfinancemetre.security.principal.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnBean(FamilyDashboardSnapshotRepository.class)
@RequiredArgsConstructor
public class DeviceDashboardReadService {

	private static final DateTimeFormatter UPDATED_AT_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");

	private final FamilyDashboardSnapshotRepository familyDashboardSnapshotRepository;

	/**
	 * Возвращает готовый dashboard для физического устройства по данным уже аутентифицированного устройства.
	 *
	 * @param devicePrincipal аутентифицированное устройство с идентификатором семьи
	 * @return готовый payload dashboard для экрана устройства
	 * @throws DashboardNotReadyException если для семьи еще не рассчитан ни один dashboard snapshot
	 */
	@Transactional(readOnly = true)
	public DeviceDashboardResponse readDashboard(DevicePrincipal devicePrincipal) {
		FamilyDashboardSnapshotEntity snapshot = familyDashboardSnapshotRepository
			.findFirstByFamilyIdOrderByPeriodYearDescPeriodMonthDesc(devicePrincipal.familyId())
			.orElseThrow(DashboardNotReadyException::new);

		ZoneId familyZoneId = ZoneId.of(snapshot.getFamily().getTimezone());
		OffsetDateTime generatedAt = snapshot.getCalculatedAt()
			.atZoneSameInstant(familyZoneId)
			.toOffsetDateTime();

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
}
