package org.alexmond.gotmpl4j.html;

/**
 * The HTML element when inside a start tag or special body. Certain elements (e.g.
 * {@code <script>}, {@code <style>}) have bodies treated differently from
 * {@link State#TEXT}. Mirrors Go {@code html/template}'s {@code element} (context.go);
 * declaration order matches Go's {@code iota} order.
 */
public enum Element {

	/** Outside a special tag or special element body. */
	NONE,
	/** A {@code <script>} raw-text element with JS (or no) type. */
	SCRIPT,
	/** A {@code <style>} raw-text element. */
	STYLE,
	/** A {@code <textarea>} RCDATA element. */
	TEXTAREA,
	/** A {@code <title>} RCDATA element. */
	TITLE,
	/** A {@code <meta>} element. */
	META

}
