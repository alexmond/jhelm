package org.alexmond.gotmpl4j.spring;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration for the gotmpl4j Spring Boot starter, mirroring the conventions of
 * {@code spring.mustache.*} / {@code spring.freemarker.*}.
 */
@ConfigurationProperties("gotmpl4j")
public class Gotmpl4jProperties {

	/** Whether the autoconfiguration is enabled. */
	private boolean enabled = true;

	/**
	 * Prefix (a Spring resource location) templates are loaded from. Default
	 * {@code classpath:/templates/}.
	 */
	private String prefix = "classpath:/templates/";

	/** File-name suffix of templates under {@link #prefix}. */
	private String suffix = ".gotmpl";

	/** Charset used to read template files and render views. */
	private Charset charset = StandardCharsets.UTF_8;

	/** Whether to check that the templates location exists. */
	private boolean checkTemplateLocation = true;

	/**
	 * Whether to cache the compiled template set. Disable during development so template
	 * edits are picked up without a restart.
	 */
	private boolean cache = true;

	/** View names that can be resolved (null means all). */
	private String[] viewNames;

	/** Name of the RequestContext attribute for all views, or null to use the default. */
	private String requestContextAttribute;

	/** Servlet-specific (Spring MVC) view settings. */
	private final Servlet servlet = new Servlet();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getPrefix() {
		return this.prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * Deprecated alias for {@link #getPrefix()}.
	 * @return the template location (the prefix)
	 * @deprecated use {@link #getPrefix()} / {@code gotmpl4j.prefix} instead
	 */
	@Deprecated(since = "0.1.0")
	@DeprecatedConfigurationProperty(replacement = "gotmpl4j.prefix")
	public String getTemplateLocation() {
		return this.prefix;
	}

	/**
	 * Deprecated alias for {@link #setPrefix(String)}.
	 * @param templateLocation the template location (the prefix)
	 * @deprecated use {@link #setPrefix(String)} / {@code gotmpl4j.prefix} instead
	 */
	@Deprecated(since = "0.1.0")
	public void setTemplateLocation(String templateLocation) {
		this.prefix = templateLocation;
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

	public String getCharsetName() {
		return (this.charset != null) ? this.charset.name() : null;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public boolean isCheckTemplateLocation() {
		return this.checkTemplateLocation;
	}

	public void setCheckTemplateLocation(boolean checkTemplateLocation) {
		this.checkTemplateLocation = checkTemplateLocation;
	}

	public boolean isCache() {
		return this.cache;
	}

	public void setCache(boolean cache) {
		this.cache = cache;
	}

	public String[] getViewNames() {
		return (this.viewNames != null) ? this.viewNames.clone() : null;
	}

	public void setViewNames(String[] viewNames) {
		this.viewNames = (viewNames != null) ? viewNames.clone() : null;
	}

	public String getRequestContextAttribute() {
		return this.requestContextAttribute;
	}

	public void setRequestContextAttribute(String requestContextAttribute) {
		this.requestContextAttribute = requestContextAttribute;
	}

	public Servlet getServlet() {
		return this.servlet;
	}

	/**
	 * Servlet (Spring MVC) view-resolver settings, mirroring
	 * {@code spring.mustache.servlet}.
	 */
	public static class Servlet {

		/** Content-Type value written for resolved views. */
		private String contentType = "text/html";

		/** Whether HttpServletRequest attributes are allowed to merge into the model. */
		private boolean exposeRequestAttributes;

		/** Whether HttpSession attributes are allowed to merge into the model. */
		private boolean exposeSessionAttributes;

		/**
		 * Whether a request attribute may override a model attribute of the same name.
		 */
		private boolean allowRequestOverride;

		/**
		 * Whether a session attribute may override a model attribute of the same name.
		 */
		private boolean allowSessionOverride;

		public String getContentType() {
			return this.contentType;
		}

		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		public boolean isExposeRequestAttributes() {
			return this.exposeRequestAttributes;
		}

		public void setExposeRequestAttributes(boolean exposeRequestAttributes) {
			this.exposeRequestAttributes = exposeRequestAttributes;
		}

		public boolean isExposeSessionAttributes() {
			return this.exposeSessionAttributes;
		}

		public void setExposeSessionAttributes(boolean exposeSessionAttributes) {
			this.exposeSessionAttributes = exposeSessionAttributes;
		}

		public boolean isAllowRequestOverride() {
			return this.allowRequestOverride;
		}

		public void setAllowRequestOverride(boolean allowRequestOverride) {
			this.allowRequestOverride = allowRequestOverride;
		}

		public boolean isAllowSessionOverride() {
			return this.allowSessionOverride;
		}

		public void setAllowSessionOverride(boolean allowSessionOverride) {
			this.allowSessionOverride = allowSessionOverride;
		}

	}

}
