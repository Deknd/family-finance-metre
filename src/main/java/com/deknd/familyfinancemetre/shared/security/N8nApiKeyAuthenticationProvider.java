package com.deknd.familyfinancemetre.shared.security;

import com.deknd.familyfinancemetre.shared.config.ApplicationProperties;
import com.deknd.familyfinancemetre.shared.security.principal.IntegrationPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class N8nApiKeyAuthenticationProvider implements AuthenticationProvider {

	private static final String INVALID_API_KEY_CODE = "INVALID_API_KEY";
	private static final String INVALID_API_KEY_MESSAGE = "API key is invalid";

	private final ApplicationProperties applicationProperties;

	public N8nApiKeyAuthenticationProvider(ApplicationProperties applicationProperties) {
		this.applicationProperties = applicationProperties;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String providedApiKey = (String) authentication.getCredentials();
		String expectedApiKey = applicationProperties.security().n8nApiKey();

		if (providedApiKey == null || !MessageDigest.isEqual(
			providedApiKey.getBytes(StandardCharsets.UTF_8),
			expectedApiKey.getBytes(StandardCharsets.UTF_8)
		)) {
			throw new HeaderAuthenticationException(INVALID_API_KEY_CODE, INVALID_API_KEY_MESSAGE);
		}

		return new N8nApiKeyAuthenticationToken(new IntegrationPrincipal("n8n"));
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return N8nApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
	}
}


