package org.alexmond.gotmpl4j.html;

/**
 * A code for a kind of error encountered while contextually escaping a template. Mirrors
 * Go {@code html/template}'s {@code ErrorCode} (error.go); declaration order matches Go's
 * {@code iota} order.
 */
public enum EscapeErrorCode {

	/** No error. */
	OK,
	/** A pipeline appears in an ambiguous context within a URL. */
	ERR_AMBIG_CONTEXT,
	/** Malformed HTML: expected space, attr name or end of tag. */
	ERR_BAD_HTML,
	/**
	 * {@code {{if}}}/{@code {{range}}}/{@code {{with}}} branches end in different
	 * contexts.
	 */
	ERR_BRANCH_END,
	/** A template ends in a non-text context. */
	ERR_END_CONTEXT,
	/** A referenced template does not exist. */
	ERR_NO_SUCH_TEMPLATE,
	/** The output context for a template cannot be computed (e.g. bad recursion). */
	ERR_OUTPUT_CONTEXT,
	/** An unfinished JS regexp character set. */
	ERR_PARTIAL_CHARSET,
	/** An unfinished escape sequence (action follows a backslash). */
	ERR_PARTIAL_ESCAPE,
	/** A range loop re-entry would end in a different context. */
	ERR_RANGE_LOOP_REENTRY,
	/** A {@code /} could start either a division or a regexp. */
	ERR_SLASH_AMBIG,
	/** A predefined escaper ({@code html}/{@code urlquery}) is disallowed here. */
	ERR_PREDEFINED_ESCAPER,
	/**
	 * A pipeline appears in a JS template literal (deprecated in Go; retained for
	 * parity).
	 */
	ERR_JS_TEMPLATE

}
