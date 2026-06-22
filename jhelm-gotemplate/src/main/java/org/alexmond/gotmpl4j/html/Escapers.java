package org.alexmond.gotmpl4j.html;

import java.util.LinkedHashMap;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.GoFmt;

/**
 * Registry of the internal contextual escaper functions, keyed by the same names Go
 * {@code html/template} uses (e.g. {@code _html_template_htmlescaper}). The escape pass
 * appends these by name to action pipelines; the executor resolves them like any
 * function.
 */
public final class Escapers {

	/**
	 * The string emitted when unsafe content reaches a CSS/URL context at runtime (Go's
	 * {@code "ZgotmplZ"}).
	 */
	public static final String FILTER_FAILSAFE = "ZgotmplZ";

	private Escapers() {
	}

	/**
	 * The internal escaper functions, by Go name.
	 * @return an ordered map of escaper name to implementation
	 */
	public static Map<String, Function> escapers() {
		Map<String, Function> m = new LinkedHashMap<>();
		m.put("_html_template_attrescaper", HtmlEscapers::attrEscaper);
		m.put("_html_template_commentescaper", HtmlEscapers::commentEscaper);
		m.put("_html_template_htmlescaper", HtmlEscapers::htmlEscaper);
		m.put("_html_template_htmlnamefilter", HtmlEscapers::htmlNameFilter);
		m.put("_html_template_nospaceescaper", HtmlEscapers::htmlNospaceEscaper);
		m.put("_html_template_rcdataescaper", HtmlEscapers::rcdataEscaper);
		m.put("_html_template_cssescaper", CssEscapers::cssEscaper);
		m.put("_html_template_cssvaluefilter", CssEscapers::cssValueFilter);
		m.put("_html_template_jsregexpescaper", JsEscapers::jsRegexpEscaper);
		m.put("_html_template_jsstrescaper", JsEscapers::jsStrEscaper);
		m.put("_html_template_jstmpllitescaper", JsEscapers::jsTmplLitEscaper);
		m.put("_html_template_jsvalescaper", JsEscapers::jsValEscaper);
		m.put("_html_template_urlescaper", UrlEscapers::urlEscaper);
		m.put("_html_template_urlfilter", UrlEscapers::urlFilter);
		m.put("_html_template_urlnormalizer", UrlEscapers::urlNormalizer);
		m.put("_html_template_srcsetescaper", UrlEscapers::srcsetFilterAndEscaper);
		m.put("_eval_args_", Escapers::evalArgs);
		return m;
	}

	// Renders pipeline arguments to a string when merging a predefined escaper into the
	// contextual pipeline (mirrors Go's evalArgs / fmt.Sprint).
	private static String evalArgs(Object... args) {
		if (args.length == 1 && args[0] instanceof String s) {
			return s;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			if (i > 0 && !(args[i - 1] instanceof String) && !(args[i] instanceof String)) {
				sb.append(' ');
			}
			sb.append(GoFmt.sprint(args[i]));
		}
		return sb.toString();
	}

}
