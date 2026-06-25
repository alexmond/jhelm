package org.alexmond.jhelm.rest.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application that exercises the jhelm REST module as a library,
 * verifying that its controllers and services are auto-configured without manual wiring.
 */
@SpringBootApplication
@SuppressWarnings("PMD.UseUtilityClass")
public class JhelmRestSampleApplication {

	/**
	 * Application entry point.
	 * @param args command-line arguments passed to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(JhelmRestSampleApplication.class, args);
	}

}
