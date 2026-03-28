package com.deknd.familyfinancemetre.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SecurityConfiguration {

	@Bean
	AuthenticationManager authenticationManager(List<AuthenticationProvider> authenticationProviders) {
		return new ProviderManager(authenticationProviders);
	}

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		DeviceAuthenticationFilter deviceAuthenticationFilter,
		N8nApiKeyAuthenticationFilter n8nApiKeyAuthenticationFilter,
		JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint
	) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.rememberMe(AbstractHttpConfigurer::disable)
			.anonymous(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(jsonAuthenticationEntryPoint))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health", "/error").permitAll()
				.requestMatchers("/api/v1/device/**").authenticated()
				.requestMatchers("/api/v1/intake/**").authenticated()
				.anyRequest().denyAll()
			)
			.addFilterBefore(deviceAuthenticationFilter, AuthorizationFilter.class)
			.addFilterBefore(n8nApiKeyAuthenticationFilter, AuthorizationFilter.class);

		return http.build();
	}
}

