package org.alexmond.gotmpl4j.html;

import java.util.Map;

/**
 * Maps HTML attribute names to the {@link ContentType} of their value, mirroring Go
 * {@code html/template}'s {@code attrTypeMap} and {@code attrType} (attr.go). Used by the
 * transition machine to decide the context an attribute value is interpolated in (URL,
 * CSS, JS, srcset, …).
 */
final class AttrTypes {

	// Derived from HTML5 plus the %URI-typed HTML4 attributes. An attribute that affects
	// or
	// can mask the encoding/interpretation of other content, or the contents/idempotency/
	// credentials of a network message, is UNSAFE.
	private static final Map<String, ContentType> ATTR_TYPE_MAP = buildMap();

	private AttrTypes() {
	}

	/**
	 * A conservative (upper-bound-on-authority) guess at the type of the lower-case named
	 * attribute.
	 * @param attrName the lower-case attribute name
	 * @return the content type of the attribute's value
	 */
	static ContentType attrType(String attrName) {
		String name = attrName;
		if (name.startsWith("data-")) {
			// Strip data- so the custom-attribute heuristics below apply widely.
			name = name.substring(5);
		}
		else {
			int colon = name.indexOf(':');
			if (colon >= 0) {
				String prefix = name.substring(0, colon);
				if (prefix.equals("xmlns")) {
					return ContentType.URL;
				}
				// Treat svg:href and xlink:href as href below.
				name = name.substring(colon + 1);
			}
		}
		ContentType mapped = ATTR_TYPE_MAP.get(name);
		if (mapped != null) {
			return mapped;
		}
		// Partial event-handler names are script.
		if (name.startsWith("on")) {
			return ContentType.JS;
		}
		// Heuristics to prevent "javascript:..." injection in custom data/other
		// attributes
		// (developers store URL content in attrs containing src/uri/url).
		if (name.contains("src") || name.contains("uri") || name.contains("url")) {
			return ContentType.URL;
		}
		return ContentType.PLAIN;
	}

	private static Map<String, ContentType> buildMap() {
		ContentType plain = ContentType.PLAIN;
		ContentType url = ContentType.URL;
		ContentType unsafe = ContentType.UNSAFE;
		ContentType css = ContentType.CSS;
		ContentType html = ContentType.HTML;
		ContentType srcset = ContentType.SRCSET;
		return Map.ofEntries(Map.entry("accept", plain), Map.entry("accept-charset", unsafe), Map.entry("action", url),
				Map.entry("alt", plain), Map.entry("archive", url), Map.entry("async", unsafe),
				Map.entry("autocomplete", plain), Map.entry("autofocus", plain), Map.entry("autoplay", plain),
				Map.entry("background", url), Map.entry("border", plain), Map.entry("checked", plain),
				Map.entry("cite", url), Map.entry("challenge", unsafe), Map.entry("charset", unsafe),
				Map.entry("class", plain), Map.entry("classid", url), Map.entry("codebase", url),
				Map.entry("cols", plain), Map.entry("colspan", plain), Map.entry("content", unsafe),
				Map.entry("contenteditable", plain), Map.entry("contextmenu", plain), Map.entry("controls", plain),
				Map.entry("coords", plain), Map.entry("crossorigin", unsafe), Map.entry("data", url),
				Map.entry("datetime", plain), Map.entry("default", plain), Map.entry("defer", unsafe),
				Map.entry("dir", plain), Map.entry("dirname", plain), Map.entry("disabled", plain),
				Map.entry("draggable", plain), Map.entry("dropzone", plain), Map.entry("enctype", unsafe),
				Map.entry("for", plain), Map.entry("form", unsafe), Map.entry("formaction", url),
				Map.entry("formenctype", unsafe), Map.entry("formmethod", unsafe), Map.entry("formnovalidate", unsafe),
				Map.entry("formtarget", plain), Map.entry("headers", plain), Map.entry("height", plain),
				Map.entry("hidden", plain), Map.entry("high", plain), Map.entry("href", url),
				Map.entry("hreflang", plain), Map.entry("http-equiv", unsafe), Map.entry("icon", url),
				Map.entry("id", plain), Map.entry("ismap", plain), Map.entry("keytype", unsafe),
				Map.entry("kind", plain), Map.entry("label", plain), Map.entry("lang", plain),
				Map.entry("language", unsafe), Map.entry("list", plain), Map.entry("longdesc", url),
				Map.entry("loop", plain), Map.entry("low", plain), Map.entry("manifest", url), Map.entry("max", plain),
				Map.entry("maxlength", plain), Map.entry("media", plain), Map.entry("mediagroup", plain),
				Map.entry("method", unsafe), Map.entry("min", plain), Map.entry("multiple", plain),
				Map.entry("name", plain), Map.entry("novalidate", unsafe), Map.entry("open", plain),
				Map.entry("optimum", plain), Map.entry("pattern", unsafe), Map.entry("placeholder", plain),
				Map.entry("poster", url), Map.entry("profile", url), Map.entry("preload", plain),
				Map.entry("pubdate", plain), Map.entry("radiogroup", plain), Map.entry("readonly", plain),
				Map.entry("rel", unsafe), Map.entry("required", plain), Map.entry("reversed", plain),
				Map.entry("rows", plain), Map.entry("rowspan", plain), Map.entry("sandbox", unsafe),
				Map.entry("spellcheck", plain), Map.entry("scope", plain), Map.entry("scoped", plain),
				Map.entry("seamless", plain), Map.entry("selected", plain), Map.entry("shape", plain),
				Map.entry("size", plain), Map.entry("sizes", plain), Map.entry("span", plain), Map.entry("src", url),
				Map.entry("srcdoc", html), Map.entry("srclang", plain), Map.entry("srcset", srcset),
				Map.entry("start", plain), Map.entry("step", plain), Map.entry("style", css),
				Map.entry("tabindex", plain), Map.entry("target", plain), Map.entry("title", plain),
				Map.entry("type", unsafe), Map.entry("usemap", url), Map.entry("value", unsafe),
				Map.entry("width", plain), Map.entry("wrap", plain), Map.entry("xmlns", url));
	}

}
