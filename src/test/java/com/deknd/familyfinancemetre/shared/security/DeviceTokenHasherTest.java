package com.deknd.familyfinancemetre.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTokenHasherTest {

	private final DeviceTokenHasher deviceTokenHasher = new DeviceTokenHasher();

	@Test
	void hashesSeedDeviceTokenWithSha256LowercaseHex() {
		assertThat(deviceTokenHasher.hash("local-device-token-family-001"))
			.isEqualTo("e9bdbf88b2ec36a3e0bd9d60f2cd413a1631d5dd2f6053d342dc27d24fa4a447");
	}
}

