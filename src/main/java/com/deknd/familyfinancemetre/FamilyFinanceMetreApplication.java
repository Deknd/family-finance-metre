package com.deknd.familyfinancemetre;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FamilyFinanceMetreApplication {

	public static void main(String[] args) {
		SpringApplication.run(FamilyFinanceMetreApplication.class, args);
	}

}
