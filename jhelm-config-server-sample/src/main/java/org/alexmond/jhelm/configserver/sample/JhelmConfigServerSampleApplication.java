package org.alexmond.jhelm.configserver.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * A runnable Spring Cloud Config Server for jhelm value profiles, with <b>security</b>
 * (HTTP basic auth) and <b>encryption</b> ({@code encrypt.key}) enabled. It serves the
 * example configs under {@code src/main/resources/config/} — a base document, a
 * {@code prod} profile (gated with {@code spring.config.activate.on-profile}), a
 * {@code -prod} sidecar, and {@code {cipher}} secrets.
 * <p>
 * Intended both as a DevOps starting point and as the fixture jhelm's tests ground
 * against. See the module README for how to run it and point jhelm at it.
 */
@SpringBootApplication
@EnableConfigServer
@SuppressWarnings("PMD.UseUtilityClass")
public class JhelmConfigServerSampleApplication {

	/**
	 * Application entry point.
	 * @param args command-line arguments passed to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(JhelmConfigServerSampleApplication.class, args);
	}

}
