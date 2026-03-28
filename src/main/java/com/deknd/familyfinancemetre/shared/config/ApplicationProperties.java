package com.deknd.familyfinancemetre.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("app")
public record ApplicationProperties(
	@NotNull ZoneId timezone,
	@NotNull @Valid Security security,
	@NotNull @Valid Integrations integrations,
	@NotNull @Valid Scheduler scheduler
) {

	public record Security(
		@NotBlank String n8nApiKey
	) {
	}

	public record Integrations(
		@NotNull @Valid N8n n8n
	) {
	}

	public record N8n(
		@NotNull URI webhookUrl,
		@NotBlank String bearerToken,
		@NotNull URI callbackSubmitUrl,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout
	) {
	}

	public record Scheduler(
		@NotNull @Valid PayrollCollection payrollCollection
	) {
	}

	public record PayrollCollection(
		@NotNull Boolean enabled,
		@NotBlank String cron
	) {
	}
}

