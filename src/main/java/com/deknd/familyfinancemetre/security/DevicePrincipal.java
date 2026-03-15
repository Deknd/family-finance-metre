package com.deknd.familyfinancemetre.security;

import java.util.UUID;

public record DevicePrincipal(
	UUID deviceId,
	UUID familyId,
	String deviceName
) {
}
