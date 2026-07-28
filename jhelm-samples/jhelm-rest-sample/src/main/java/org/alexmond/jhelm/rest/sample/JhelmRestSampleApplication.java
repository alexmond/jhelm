package org.alexmond.jhelm.rest.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application that exercises the jhelm REST module as a library,
 * verifying that its controllers and services are auto-configured without manual wiring.
 *
 * <p>
 * <strong>Note:</strong> this is a DEMO/sample application only. It is unauthenticated
 * and is not intended for production use. For real deployments, secure the
 * {@code jhelm-rest-starter} behind your own authentication (for example Spring
 * Security).
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
