package org.alexmond.gotmpl4j.spring;

import java.io.IOException;
import java.io.StringWriter;

import org.alexmond.gotmpl4j.GoTemplate;
import org.alexmond.gotmpl4j.TemplateException;

/**
 * Thin, stateless service over the gotmpl4j engine for Spring applications. Each call
 * parses and executes a fresh {@link GoTemplate} (the engine is per-render stateful, so a
 * singleton template cannot be shared). Sprig/Helm functions are discovered via the
 * ServiceLoader on the classpath.
 */
public class GoTemplateService {

	/**
	 * Renders a Go template against the given data.
	 * @param name a name for the template (used in error messages)
	 * @param template the template source
	 * @param data the root data object (the template's {@code .})
	 * @return the rendered output
	 * @throws GoTemplateException if parsing or execution fails
	 */
	public String render(String name, String template, Object data) {
		try {
			GoTemplate goTemplate = new GoTemplate();
			goTemplate.parse(name, template);
			StringWriter writer = new StringWriter();
			goTemplate.execute(name, data, writer);
			return writer.toString();
		}
		catch (TemplateException | IOException ex) {
			throw new GoTemplateException("Failed to render template '" + name + "'", ex);
		}
	}

}
