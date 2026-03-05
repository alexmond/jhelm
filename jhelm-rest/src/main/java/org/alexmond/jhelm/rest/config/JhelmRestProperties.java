package org.alexmond.jhelm.rest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm REST API module.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.rest")
public class JhelmRestProperties {

	/**
	 * Base path for all jhelm REST endpoints.
	 */
	private String basePath = "/api/v1";

}
