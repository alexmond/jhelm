package org.alexmond.gotmpl4j.spring;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the gotmpl4j Spring Boot starter, mirroring the conventions of
 * {@code spring.thymeleaf.*} / {@code spring.freemarker.*}.
 */
@ConfigurationProperties("gotmpl4j")
public class Gotmpl4jProperties {

	/** Whether the autoconfiguration is enabled. */
	private boolean enabled = true;

	/**
	 * Location templates are loaded from (a Spring resource location). Default
	 * {@code classpath:/templates/}.
	 */
	private String templateLocation = "classpath:/templates/";

	/** File-name suffix of templates under {@link #templateLocation}. */
	private String suffix = ".gotmpl";

	/** Charset used to read template files. */
	private Charset charset = StandardCharsets.UTF_8;

	/**
	 * Whether to cache the compiled template set. Disable during development so template
	 * edits are picked up without a restart.
	 */
	private boolean cache = true;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getTemplateLocation() {
		return this.templateLocation;
	}

	public void setTemplateLocation(String templateLocation) {
		this.templateLocation = templateLocation;
	}

	public String getSuffix() {
		return this.suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	public Charset getCharset() {
		return this.charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public boolean isCache() {
		return this.cache;
	}

	public void setCache(boolean cache) {
		this.cache = cache;
	}

}
