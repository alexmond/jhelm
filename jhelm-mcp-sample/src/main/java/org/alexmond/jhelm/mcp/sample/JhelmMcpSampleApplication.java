package org.alexmond.jhelm.mcp.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application that exercises the jhelm MCP module as a library,
 * verifying that the MCP server and the jhelm read-only tools are auto-configured without
 * manual wiring.
 *
 * <p>
 * <strong>Note:</strong> this is a DEMO/sample application only. It is unauthenticated
 * and is not intended for production use. For real deployments, secure the
 * {@code jhelm-mcp-starter} behind your own authentication.
 */
@SpringBootApplication
@SuppressWarnings("PMD.UseUtilityClass")
public class JhelmMcpSampleApplication {

	/**
	 * Application entry point.
	 * @param args command-line arguments passed to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(JhelmMcpSampleApplication.class, args);
	}

}
