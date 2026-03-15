package com.deknd.familyfinancemetre.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class N8nApiKeyAuthenticationFilter extends HeaderAuthenticationFilter {

	private static final String HEADER_NAME = "X-API-Key";

	public N8nApiKeyAuthenticationFilter(
		AuthenticationManager authenticationManager,
		AuthenticationEntryPoint authenticationEntryPoint
	) {
		super(request -> request.getRequestURI().startsWith("/api/v1/intake/"), authenticationManager, authenticationEntryPoint);
	}

	@Override
	protected Authentication buildAuthentication(HttpServletRequest request) {
		return new N8nApiKeyAuthenticationToken(request.getHeader(HEADER_NAME));
	}
}
