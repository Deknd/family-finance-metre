package com.deknd.familyfinancemetre.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfiguration {

	@Bean
	Clock clock(ApplicationProperties applicationProperties) {
		return Clock.system(applicationProperties.timezone());
	}

}
