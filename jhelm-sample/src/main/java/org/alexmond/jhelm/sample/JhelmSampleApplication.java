package org.alexmond.jhelm.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application that depends on jhelm-core as a library. Used to verify
 * that all beans are auto-wired without manual {@code @Import} or {@code @ComponentScan}
 * configuration.
 */
@SpringBootApplication
@SuppressWarnings("PMD.UseUtilityClass")
public class JhelmSampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(JhelmSampleApplication.class, args);
	}

}
