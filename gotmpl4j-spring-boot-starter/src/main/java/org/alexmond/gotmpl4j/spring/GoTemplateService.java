package org.alexmond.gotmpl4j.spring;

import java.io.IOException;
import java.io.StringWriter;

import org.alexmond.gotmpl4j.GoTemplate;
import org.alexmond.gotmpl4j.TemplateCache;
import org.alexmond.gotmpl4j.TemplateException;

/**
 * Service over the gotmpl4j engine for Spring applications. Renders either a named view
 * loaded from the configured template location (compiled once and cached, like
 * FreeMarker/Thymeleaf) or an ad-hoc inline template string.
 *
 * <p>
 * The engine is safe to share: a compiled {@link GoTemplate} is parsed once and
 * {@code execute} builds a fresh executor per call, so concurrent renders are
 * independent. The compile-once/render-many lifecycle is owned by the engine's
 * {@link TemplateCache}; this service only wires the {@link GoTemplateLoader} into it as
 * the compile step.
 */
public class GoTemplateService {

	private final TemplateCache templates;

	private final GoTemplateFactory factory;

	/**
	 * Constructs a service with no template loader (inline rendering only), using engine
	 * default functions.
	 */
	public GoTemplateService() {
		this(new GoTemplateFactory());
	}

	/**
	 * Constructs a service with no template loader (inline rendering only).
	 * @param factory builds configured templates for inline rendering
	 */
	public GoTemplateService(GoTemplateFactory factory) {
		this.templates = null;
		this.factory = factory;
	}

	public GoTemplateService(GoTemplateLoader loader, GoTemplateFactory factory, boolean cache) {
		this.templates = new TemplateCache(loader::load, cache);
		this.factory = factory;
	}

	/**
	 * Renders a view loaded by name from the configured template location.
	 * @param viewName the template name (path under the location, without the suffix)
	 * @param data the root data object (the template's {@code .})
	 * @return the rendered output
	 * @throws GoTemplateException if the loader is absent, or loading/execution fails
	 */
	public String render(String viewName, Object data) {
		if (this.templates == null) {
			throw new GoTemplateException(
					"No template location configured; use render(name, template, data) for " + "inline templates",
					null);
		}
		return execute(this.templates.get(), viewName, data);
	}

	/**
	 * Renders an ad-hoc inline template (not loaded from the template location).
	 * @param name a name for the template (used in error messages)
	 * @param template the template source
	 * @param data the root data object
	 * @return the rendered output
	 * @throws GoTemplateException if parsing or execution fails
	 */
	public String render(String name, String template, Object data) {
		try {
			GoTemplate goTemplate = this.factory.create();
			goTemplate.parse(name, template);
			return execute(goTemplate, name, data);
		}
		catch (TemplateException ex) {
			throw new GoTemplateException("Failed to render template '" + name + "'", ex);
		}
	}

	private static String execute(GoTemplate goTemplate, String name, Object data) {
		try {
			StringWriter writer = new StringWriter();
			goTemplate.execute(name, data, writer);
			return writer.toString();
		}
		catch (TemplateException | IOException ex) {
			throw new GoTemplateException("Failed to render template '" + name + "'", ex);
		}
	}

}
