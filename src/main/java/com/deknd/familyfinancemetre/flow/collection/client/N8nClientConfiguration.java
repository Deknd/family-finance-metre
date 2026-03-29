package com.deknd.familyfinancemetre.flow.collection.client;

import com.deknd.familyfinancemetre.shared.config.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Objects;

/**
 * Конфигурация инфраструктурных бинов для outbound-вызова {@code n8n}.
 */
@Configuration(proxyBeanMethods = false)
class N8nClientConfiguration {

	private final ApplicationProperties applicationProperties;

	N8nClientConfiguration(ApplicationProperties applicationProperties) {
		this.applicationProperties = Objects.requireNonNull(
			applicationProperties,
			"Application properties must not be null"
		);
	}

	@Bean("n8nClientObjectMapper")
	ObjectMapper n8nClientObjectMapper() {
		return JsonMapper.builder().findAndAddModules().build();
	}

	@Bean("n8nRestClient")
	RestClient n8nRestClient() {
		ApplicationProperties.N8n n8nProperties = applicationProperties.integrations().n8n();
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(n8nProperties.connectTimeout())
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(n8nProperties.readTimeout());

		return RestClient.builder()
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + n8nProperties.bearerToken())
			.build();
	}
}
