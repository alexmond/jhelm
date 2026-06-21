package org.alexmond.gotmpl4j.html;

import java.util.Objects;

/**
 * A string of content from a trusted source, tagged with the {@link ContentType} it is
 * safe for. When the contextual auto-escaper (HTML mode) interpolates a
 * {@code SafeContent} whose type matches the surrounding context, the value is emitted
 * verbatim instead of being escaped. Mirrors the named string types in Go
 * {@code html/template} — {@code template.HTML}, {@code template.CSS},
 * {@code template.JS}, {@code template.JSStr}, {@code template.URL},
 * {@code template.HTMLAttr}, {@code template.Srcset}.
 *
 * <p>
 * <strong>Using these types is a security decision:</strong> the wrapped content is
 * trusted and included verbatim, so it must come from a trusted source — never from
 * untrusted input.
 *
 * <pre>{@code
 * data.put("snippet", SafeContent.html("<b>bold</b>")); // emitted as-is in HTML text
 * }</pre>
 */
public final class SafeContent {

	private final String value;

	private final ContentType type;

	private SafeContent(String value, ContentType type) {
		this.value = Objects.requireNonNull(value, "value");
		this.type = type;
	}

	/** Wraps a known-safe HTML document fragment ({@code template.HTML}). */
	public static SafeContent html(String value) {
		return new SafeContent(value, ContentType.HTML);
	}

	/** Wraps known-safe CSS ({@code template.CSS}). */
	public static SafeContent css(String value) {
		return new SafeContent(value, ContentType.CSS);
	}

	/** Wraps known-safe HTML attributes ({@code template.HTMLAttr}). */
	public static SafeContent htmlAttr(String value) {
		return new SafeContent(value, ContentType.HTML_ATTR);
	}

	/** Wraps a known-safe ECMAScript expression ({@code template.JS}). */
	public static SafeContent js(String value) {
		return new SafeContent(value, ContentType.JS);
	}

	/** Wraps a known-safe JavaScript string body ({@code template.JSStr}). */
	public static SafeContent jsStr(String value) {
		return new SafeContent(value, ContentType.JS_STR);
	}

	/** Wraps a known-safe URL or URL substring ({@code template.URL}). */
	public static SafeContent url(String value) {
		return new SafeContent(value, ContentType.URL);
	}

	/** Wraps a known-safe {@code srcset} attribute value ({@code template.Srcset}). */
	public static SafeContent srcset(String value) {
		return new SafeContent(value, ContentType.SRCSET);
	}

	/**
	 * The wrapped content.
	 * @return the raw string value
	 */
	public String value() {
		return this.value;
	}

	/**
	 * The content type this value is trusted for.
	 * @return the content type
	 */
	public ContentType contentType() {
		return this.type;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof SafeContent other)) {
			return false;
		}
		return this.type == other.type && this.value.equals(other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value, this.type);
	}

	/**
	 * Returns the raw value, so a {@code SafeContent} stringifies to its content (e.g.
	 * when rendered in text mode or by {@code print}).
	 * @return the raw string value
	 */
	@Override
	public String toString() {
		return this.value;
	}

}
