package org.alexmond.gotmpl4j.html;

/**
 * The kind of content a value carries, used by the contextual auto-escaper to decide
 * whether (and how) to escape an interpolated value. Mirrors Go {@code html/template}'s
 * {@code contentType}.
 *
 * <p>
 * {@link #PLAIN} is ordinary, untrusted text that gets escaped for whatever context it
 * appears in. The remaining typed kinds correspond to {@link SafeContent} wrappers that
 * mark trusted content which bypasses the matching escaper.
 */
public enum ContentType {

	/** Ordinary untrusted text; escaped for the surrounding context. */
	PLAIN,

	/** Known-safe CSS ({@code template.CSS}). */
	CSS,

	/** Known-safe HTML document fragment ({@code template.HTML}). */
	HTML,

	/**
	 * Known-safe HTML attributes ({@code template.HTMLAttr}), e.g. {@code  dir="ltr"}.
	 */
	HTML_ATTR,

	/** Known-safe ECMAScript expression ({@code template.JS}). */
	JS,

	/**
	 * Known-safe JavaScript string body, to embed between quotes
	 * ({@code template.JSStr}).
	 */
	JS_STR,

	/** Known-safe URL or URL substring ({@code template.URL}). */
	URL,

	/** Known-safe {@code srcset} attribute value ({@code template.Srcset}). */
	SRCSET,

	/**
	 * Content that affects how embedded content or network messages are formed, vetted or
	 * interpreted; used by the attribute machinery for unsafe attributes.
	 */
	UNSAFE

}
