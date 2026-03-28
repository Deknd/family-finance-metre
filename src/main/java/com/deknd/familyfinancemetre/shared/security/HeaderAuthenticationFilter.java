package com.deknd.familyfinancemetre.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

abstract class HeaderAuthenticationFilter extends OncePerRequestFilter {

	private final RequestMatcher requestMatcher;
	private final AuthenticationManager authenticationManager;
	private final AuthenticationEntryPoint authenticationEntryPoint;

	protected HeaderAuthenticationFilter(
		RequestMatcher requestMatcher,
		AuthenticationManager authenticationManager,
		AuthenticationEntryPoint authenticationEntryPoint
	) {
		this.requestMatcher = requestMatcher;
		this.authenticationManager = authenticationManager;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !requestMatcher.matches(request);
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			Authentication authenticationResult = authenticationManager.authenticate(buildAuthentication(request));
			SecurityContextHolder.getContext().setAuthentication(authenticationResult);
			filterChain.doFilter(request, response);
		} catch (AuthenticationException exception) {
			SecurityContextHolder.clearContext();
			authenticationEntryPoint.commence(request, response, exception);
		}
	}

	protected abstract Authentication buildAuthentication(HttpServletRequest request);
}

