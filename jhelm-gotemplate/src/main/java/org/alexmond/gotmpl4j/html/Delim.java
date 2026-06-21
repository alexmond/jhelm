package org.alexmond.gotmpl4j.html;

/**
 * The delimiter that will end the current HTML attribute. Mirrors Go
 * {@code html/template}'s {@code delim} (context.go); declaration order matches Go's
 * {@code iota} order.
 */
public enum Delim {

	/** Outside any attribute. */
	NONE,
	/** A double quote ({@code "}) closes the attribute. */
	DOUBLE_QUOTE,
	/** A single quote ({@code '}) closes the attribute. */
	SINGLE_QUOTE,
	/** A space or {@code >} closes the attribute. */
	SPACE_OR_TAG_END

}
