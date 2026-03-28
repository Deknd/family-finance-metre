package com.deknd.familyfinancemetre.flow.dashboard.web;

import com.deknd.familyfinancemetre.flow.dashboard.dto.DeviceDashboardResponse;
import com.deknd.familyfinancemetre.shared.security.principal.DevicePrincipal;
import com.deknd.familyfinancemetre.flow.dashboard.service.DeviceDashboardReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/device")
public class DeviceDashboardController {

	private final DeviceDashboardReadService deviceDashboardReadService;

	/**
	 * Возвращает готовый dashboard для аутентифицированного физического устройства.
	 *
	 * @param principal principal аутентифицированного устройства
	 * @return payload dashboard для отображения на устройстве
	 */
	@GetMapping("/dashboard")
	public DeviceDashboardResponse readDashboard(@AuthenticationPrincipal DevicePrincipal principal) {
		return deviceDashboardReadService.readDashboard(principal);
	}
}


