package com.deknd.familyfinancemetre.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class DeviceAuthenticationFilter extends HeaderAuthenticationFilter {

	private static final String HEADER_NAME = "X-Device-Token";

	public DeviceAuthenticationFilter(
		AuthenticationManager authenticationManager,
		AuthenticationEntryPoint authenticationEntryPoint
	) {
		super(request -> request.getRequestURI().startsWith("/api/v1/device/"), authenticationManager, authenticationEntryPoint);
	}

	@Override
	protected Authentication buildAuthentication(HttpServletRequest request) {
		return new DeviceAuthenticationToken(request.getHeader(HEADER_NAME));
	}
}

