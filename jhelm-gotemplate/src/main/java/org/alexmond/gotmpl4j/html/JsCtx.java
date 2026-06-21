package org.alexmond.gotmpl4j.html;

/**
 * Whether a {@code /} starts a regular-expression literal or a division operator. Mirrors
 * Go {@code html/template}'s {@code jsCtx} (context.go); declaration order matches Go's
 * {@code iota} order.
 */
public enum JsCtx {

	/** A {@code /} would start a regexp literal. */
	REGEXP,
	/** A {@code /} would start a division operator. */
	DIV_OP,
	/** A {@code /} is ambiguous due to context joining. */
	UNKNOWN

}
