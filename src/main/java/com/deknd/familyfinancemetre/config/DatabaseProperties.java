package com.deknd.familyfinancemetre.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("spring.datasource")
public record DatabaseProperties(
	@NotBlank String url,
	@NotBlank String username,
	@NotBlank String password,
	@NotBlank String driverClassName
) {
}
