package com.fas.dentistry_data_analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class DentistryDataAnalysisApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(DentistryDataAnalysisApplication.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(DentistryDataAnalysisApplication.class);
	}
}
