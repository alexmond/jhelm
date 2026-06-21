package org.alexmond.gotmpl4j.html;

/**
 * The current HTML attribute, from {@link State#ATTR_NAME} until the tag/text states.
 * Mirrors Go {@code html/template}'s {@code attr} (context.go); declaration order matches
 * Go's {@code iota} order.
 */
public enum Attr {

	/** A normal attribute, or no attribute. */
	NONE,
	/** An event-handler attribute (its value is JavaScript). */
	SCRIPT,
	/** The {@code type} attribute of a {@code <script>} element. */
	SCRIPT_TYPE,
	/** The {@code style} attribute (its value is CSS). */
	STYLE,
	/** An attribute whose value is a URL. */
	URL,
	/** A {@code srcset} attribute. */
	SRCSET,
	/** The {@code content} attribute of a {@code <meta>} element. */
	META_CONTENT

}
