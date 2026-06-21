package org.alexmond.gotmpl4j.html;

import org.alexmond.gotmpl4j.TemplateException;
import org.alexmond.gotmpl4j.parse.Node;

/**
 * Describes a problem encountered while contextually escaping a template (HTML mode).
 * Mirrors Go {@code html/template}'s {@code Error} (error.go). It is both stored on a
 * {@link Context} to mark the infectious {@link State#ERROR} state and thrown by the
 * escape pass, so it extends the engine's {@link TemplateException} hierarchy.
 */
public final class EscapeError extends TemplateException {

	private final transient EscapeErrorCode code;

	private final transient Node node;

	private final String name;

	private final transient String description;

	/**
	 * Creates an escape error.
	 * @param code the kind of error
	 * @param node the offending node, if known (overrides name/line in the message)
	 * @param name the template name in which the error occurred
	 * @param line the source line, or 0
	 * @param description a human-readable description
	 */
	public EscapeError(EscapeErrorCode code, Node node, String name, int line, String description) {
		super(format(name, line, description), line, -1);
		this.code = code;
		this.node = node;
		this.name = name;
		this.description = description;
	}

	/**
	 * Creates an escape error from a format string, mirroring Go's {@code errorf}. The
	 * template name is filled in later by the escape pass.
	 * @param code the kind of error
	 * @param node the offending node, if known
	 * @param line the source line, or 0
	 * @param format a {@link String#format}-style format string
	 * @param args the format arguments
	 * @return a new escape error
	 */
	public static EscapeError errorf(EscapeErrorCode code, Node node, int line, String format, Object... args) {
		return new EscapeError(code, node, "", line, String.format(format, args));
	}

	/**
	 * The kind of error.
	 * @return the error code
	 */
	public EscapeErrorCode code() {
		return this.code;
	}

	/**
	 * The offending node, if known.
	 * @return the node, or {@code null}
	 */
	public Node node() {
		return this.node;
	}

	/**
	 * The template name in which the error occurred.
	 * @return the template name (possibly empty)
	 */
	public String templateName() {
		return this.name;
	}

	/**
	 * The human-readable description.
	 * @return the description
	 */
	public String description() {
		return this.description;
	}

	private static String format(String name, int line, String description) {
		if (line != 0) {
			return "html/template:" + name + ":" + line + ": " + description;
		}
		if (name != null && !name.isEmpty()) {
			return "html/template:" + name + ": " + description;
		}
		return "html/template: " + description;
	}

}
