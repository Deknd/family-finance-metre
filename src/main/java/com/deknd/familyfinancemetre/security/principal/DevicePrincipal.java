package com.deknd.familyfinancemetre.security.principal;

import java.util.UUID;

public record DevicePrincipal(
	UUID deviceId,
	UUID familyId,
	String deviceName
) {
}
