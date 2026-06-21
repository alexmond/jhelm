package org.alexmond.gotmpl4j;

import java.io.IOException;
import java.io.Writer;

/**
 * An immutable handle to a single named template within a compiled {@link GoTemplate}
 * set, bound once so callers render it without repeating the name.
 *
 * <p>
 * Obtain one via {@link GoTemplate#compiled(String)}. The underlying {@link GoTemplate}
 * is parsed once and {@code execute} builds a fresh executor per call, so a handle is
 * safe to reuse across threads.
 *
 * <pre>{@code
 * CompiledTemplate greeting = template.compiled("greeting");
 * String out = greeting.render(Map.of("Name", "world"));
 * }</pre>
 */
public final class CompiledTemplate {

	private final GoTemplate template;

	private final String name;

	CompiledTemplate(GoTemplate template, String name) {
		this.template = template;
		this.name = name;
	}

	/**
	 * Renders this template to a {@code String}.
	 * @param data the root data object (the template's {@code .})
	 * @return the rendered output
	 * @throws TemplateExecutionException if execution fails
	 */
	public String render(Object data) throws TemplateExecutionException {
		try {
			return this.template.render(this.name, data);
		}
		catch (TemplateNotFoundException ex) {
			// The name was validated when this handle was created and the set is
			// immutable,
			// so this cannot normally happen; surface it as an execution failure.
			throw new TemplateExecutionException("Bound template '" + this.name + "' is no longer present", ex);
		}
	}

	/**
	 * Renders this template to the given {@link Writer}.
	 * @param data the root data object (the template's {@code .})
	 * @param writer the destination
	 * @throws IOException if writing fails
	 * @throws TemplateException if execution fails
	 */
	public void render(Object data, Writer writer) throws IOException, TemplateException {
		this.template.execute(this.name, data, writer);
	}

	/**
	 * The bound template name.
	 * @return the template name
	 */
	public String name() {
		return this.name;
	}

}
