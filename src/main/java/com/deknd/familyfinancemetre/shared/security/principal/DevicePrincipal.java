package com.deknd.familyfinancemetre.shared.security.principal;

import java.util.UUID;

public record DevicePrincipal(
	UUID deviceId,
	UUID familyId,
	String deviceName
) {
}

