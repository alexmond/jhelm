package org.alexmond.gotmpl4j.html;

/**
 * A part of an RFC 3986 hierarchical URL, used to pick a URL encoding strategy. Mirrors
 * Go {@code html/template}'s {@code urlPart} (context.go); declaration order matches Go's
 * {@code iota} order.
 */
public enum UrlPart {

	/** Not in a URL, or possibly at its very start. */
	NONE,
	/** In the scheme, authority or path (before any {@code ?}). */
	PRE_QUERY,
	/** In the query or fragment (after {@code ?}). */
	QUERY_OR_FRAG,
	/** Ambiguous due to joining of contexts before and after the query separator. */
	UNKNOWN

}
