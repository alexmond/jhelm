package org.alexmond.gotmpl4j.html;

/**
 * A high-level HTML-parser state describing where, within an HTML document, the output of
 * a template node lands. Mirrors Go {@code html/template}'s {@code state} (context.go);
 * the declaration order matches Go's {@code iota} order.
 *
 * <p>
 * The zero value {@link #TEXT} is the start state for a template that produces an HTML
 * fragment.
 */
public enum State {

	/** Parsed character data, outside any tag, directive, comment or special body. */
	TEXT,
	/** Before an HTML attribute or the end of a tag. */
	TAG,
	/** Inside an attribute name: between the carets in {@code  ^name^ = value}. */
	ATTR_NAME,
	/** After an attribute name but before any {@code =}. */
	AFTER_NAME,
	/** After the {@code =} but before the value. */
	BEFORE_VALUE,
	/** Inside an {@code <!-- HTML comment -->}. */
	HTML_CMT,
	/** Inside an RCDATA element body ({@code <textarea>} or {@code <title>}). */
	RCDATA,
	/** Inside an HTML attribute whose content is plain text. */
	ATTR,
	/** Inside an HTML attribute whose content is a URL. */
	URL,
	/** Inside an HTML {@code srcset} attribute. */
	SRCSET,
	/** Inside an event handler or {@code <script>} element. */
	JS,
	/** Inside a JavaScript double-quoted string. */
	JS_DQ_STR,
	/** Inside a JavaScript single-quoted string. */
	JS_SQ_STR,
	/** Inside a JavaScript back-quoted template literal. */
	JS_TMPL_LIT,
	/** Inside a JavaScript regexp literal. */
	JS_REGEXP,
	/** Inside a JavaScript {@code /* block comment *}{@code /}. */
	JS_BLOCK_CMT,
	/** Inside a JavaScript {@code //} line comment. */
	JS_LINE_CMT,
	/** Inside a JavaScript {@code <!--} HTML-like comment. */
	JS_HTML_OPEN_CMT,
	/** Inside a JavaScript {@code -->} HTML-like comment. */
	JS_HTML_CLOSE_CMT,
	/** Inside a {@code <style>} element or {@code style} attribute. */
	CSS,
	/** Inside a CSS double-quoted string. */
	CSS_DQ_STR,
	/** Inside a CSS single-quoted string. */
	CSS_SQ_STR,
	/** Inside a CSS double-quoted {@code url("...")}. */
	CSS_DQ_URL,
	/** Inside a CSS single-quoted {@code url('...')}. */
	CSS_SQ_URL,
	/** Inside a CSS unquoted {@code url(...)}. */
	CSS_URL,
	/** Inside a CSS {@code /* block comment *}{@code /}. */
	CSS_BLOCK_CMT,
	/** Inside a CSS {@code //} line comment. */
	CSS_LINE_CMT,
	/** An infectious error state outside any valid HTML/CSS/JS construct. */
	ERROR,
	/** Inside an HTML {@code <meta>} element {@code content} attribute. */
	META_CONTENT,
	/** Inside a {@code url=} part of a {@code <meta>} {@code content} attribute. */
	META_CONTENT_URL,
	/** Unreachable code after a {@code {{break}}} or {@code {{continue}}}. */
	DEAD;

	/**
	 * Whether this state holds content meant for template authors, not end users or
	 * machines (i.e. a comment).
	 * @return {@code true} for the HTML/JS/CSS comment states
	 */
	public boolean isComment() {
		return switch (this) {
			case HTML_CMT, JS_BLOCK_CMT, JS_LINE_CMT, JS_HTML_OPEN_CMT, JS_HTML_CLOSE_CMT, CSS_BLOCK_CMT,
					CSS_LINE_CMT ->
				true;
			default -> false;
		};
	}

	/**
	 * Whether this state occurs solely inside an HTML tag.
	 * @return {@code true} inside a tag (name/attr machinery)
	 */
	public boolean isInTag() {
		return switch (this) {
			case TAG, ATTR_NAME, AFTER_NAME, BEFORE_VALUE, ATTR -> true;
			default -> false;
		};
	}

	/**
	 * Whether this is one of the literal states within a {@code <script>} tag, where
	 * {@code <!--}, {@code <script}, and {@code </script} need special treatment.
	 * @return {@code true} inside a JS string/regexp literal
	 */
	public boolean isInScriptLiteral() {
		return switch (this) {
			case JS_DQ_STR, JS_SQ_STR, JS_TMPL_LIT, JS_REGEXP -> true;
			default -> false;
		};
	}

}
