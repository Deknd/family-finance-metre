package com.deknd.familyfinancemetre.shared.security;

import com.deknd.familyfinancemetre.shared.security.principal.DevicePrincipal;
import com.deknd.familyfinancemetre.shared.security.principal.IntegrationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityTestController {

	@GetMapping("/api/v1/device/test-auth")
	@ResponseStatus(HttpStatus.OK)
	DeviceAuthResponse deviceAuth(@AuthenticationPrincipal DevicePrincipal principal) {
		return new DeviceAuthResponse(
			principal.deviceId().toString(),
			principal.familyId().toString(),
			principal.deviceName()
		);
	}

	@PostMapping("/api/v1/intake/test-auth")
	@ResponseStatus(HttpStatus.OK)
	IntegrationAuthResponse intakeAuth(@AuthenticationPrincipal IntegrationPrincipal principal) {
		return new IntegrationAuthResponse(principal.integrationName());
	}

	record DeviceAuthResponse(
		String deviceId,
		String familyId,
		String deviceName
	) {
	}

	record IntegrationAuthResponse(
		String integrationName
	) {
	}
}


