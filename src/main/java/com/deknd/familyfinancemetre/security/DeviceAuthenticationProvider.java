package com.deknd.familyfinancemetre.security;

import com.deknd.familyfinancemetre.entity.DeviceEntity;
import com.deknd.familyfinancemetre.entity.enums.DeviceStatus;
import com.deknd.familyfinancemetre.repository.DeviceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class DeviceAuthenticationProvider implements AuthenticationProvider {

	private static final String INVALID_DEVICE_TOKEN_CODE = "INVALID_DEVICE_TOKEN";
	private static final String INVALID_DEVICE_TOKEN_MESSAGE = "Device token is invalid";

	private final DeviceRepository deviceRepository;
	private final DeviceTokenHasher deviceTokenHasher;

	public DeviceAuthenticationProvider(
		ObjectProvider<DeviceRepository> deviceRepositoryProvider,
		DeviceTokenHasher deviceTokenHasher
	) {
		this.deviceRepository = deviceRepositoryProvider.getIfAvailable();
		this.deviceTokenHasher = deviceTokenHasher;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String rawToken = (String) authentication.getCredentials();
		if (rawToken == null || rawToken.isBlank()) {
			throw new HeaderAuthenticationException(INVALID_DEVICE_TOKEN_CODE, INVALID_DEVICE_TOKEN_MESSAGE);
		}
		if (deviceRepository == null) {
			throw new HeaderAuthenticationException(INVALID_DEVICE_TOKEN_CODE, INVALID_DEVICE_TOKEN_MESSAGE);
		}

		String tokenHash = deviceTokenHasher.hash(rawToken);
		DeviceEntity device = deviceRepository.findByDeviceTokenHashAndStatus(tokenHash, DeviceStatus.ACTIVE)
			.orElseThrow(() -> new HeaderAuthenticationException(INVALID_DEVICE_TOKEN_CODE, INVALID_DEVICE_TOKEN_MESSAGE));

		return new DeviceAuthenticationToken(new DevicePrincipal(
			device.getId(),
			device.getFamily().getId(),
			device.getName()
		));
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return DeviceAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
