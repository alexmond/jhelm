package org.alexmond.gotmpl4j.spring;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.alexmond.gotmpl4j.GoTemplate;
import org.alexmond.gotmpl4j.TemplateParseException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Loads every template under {@link Gotmpl4jProperties#getTemplateLocation()} (matching
 * the configured suffix) into a single {@link GoTemplate} set, so a view rendered by name
 * can reference the others via {@code {{ template "..." }}} or shared {@code {{ define
 * }}} blocks. Each file is registered under its path relative to the location, with the
 * suffix stripped (e.g. {@code templates/layouts/base.gotmpl} -> {@code layouts/base}).
 */
public class GoTemplateLoader {

	private final ResourcePatternResolver resolver;

	private final Gotmpl4jProperties properties;

	private final GoTemplateFactory factory;

	public GoTemplateLoader(ResourcePatternResolver resolver, Gotmpl4jProperties properties,
			GoTemplateFactory factory) {
		this.resolver = resolver;
		this.properties = properties;
		this.factory = factory;
	}

	public GoTemplateLoader(org.springframework.core.io.ResourceLoader resourceLoader, Gotmpl4jProperties properties,
			GoTemplateFactory factory) {
		this(new PathMatchingResourcePatternResolver(resourceLoader), properties, factory);
	}

	/**
	 * Reads and parses every matching template into one fresh {@link GoTemplate} set.
	 * @return the compiled template set
	 * @throws GoTemplateException if a template cannot be read or parsed
	 */
	public GoTemplate load() {
		String location = withTrailingSlash(this.properties.getPrefix());
		String pattern = toPatternPrefix(location) + "**/*" + this.properties.getSuffix();
		try {
			GoTemplate goTemplate = this.factory.create();
			Resource[] resources = this.resolver.getResources(pattern);
			for (Resource resource : resources) {
				String name = templateName(resource, location);
				try (Reader reader = new InputStreamReader(resource.getInputStream(), this.properties.getCharset())) {
					goTemplate.parse(name, reader);
				}
			}
			return goTemplate;
		}
		catch (IOException | TemplateParseException ex) {
			throw new GoTemplateException("Failed to load templates from '" + location + "'", ex);
		}
	}

	private static String withTrailingSlash(String location) {
		return location.endsWith("/") ? location : location + "/";
	}

	// classpath:/templates/ -> classpath*:/templates/ so jar entries are matched too.
	private static String toPatternPrefix(String location) {
		if (location.startsWith("classpath:")) {
			return "classpath*:" + location.substring("classpath:".length());
		}
		return location;
	}

	private String templateName(Resource resource, String location) throws IOException {
		// The location's bare path (e.g. "templates/") appears in every matched
		// resource's
		// URL; the part after its last occurrence, minus the suffix, is the template
		// name.
		String bare = location.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*:", "").replaceFirst("^/+", "");
		String url = resource.getURL().toString();
		int idx = (bare.isEmpty()) ? -1 : url.lastIndexOf(bare);
		String relative = (idx >= 0) ? url.substring(idx + bare.length()) : resource.getFilename();
		if (relative == null) {
			relative = "";
		}
		relative = relative.replaceFirst("^/+", "");
		String suffix = this.properties.getSuffix();
		return relative.endsWith(suffix) ? relative.substring(0, relative.length() - suffix.length()) : relative;
	}

}
