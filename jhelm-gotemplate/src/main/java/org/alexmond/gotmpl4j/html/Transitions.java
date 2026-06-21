package org.alexmond.gotmpl4j.html;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * The HTML/JS/CSS context transition machine, ported from Go {@code html/template}'s
 * transition.go. Given a {@link Context} and a run of literal template text (as UTF-8
 * {@code byte[]}, to keep byte offsets identical to Go), {@link #transition} advances the
 * context and reports how many bytes it consumed. The escape pass drives this over the
 * text between actions to learn each action's context.
 */
final class Transitions {

	private static final byte[] COMMENT_START = Bytes.utf8("<!--");

	private static final byte[] COMMENT_END = Bytes.utf8("-->");

	private static final byte[] BLOCK_COMMENT_END = Bytes.utf8("*/");

	private static final byte[] SPECIAL_TAG_END_PREFIX = Bytes.utf8("</");

	private static final String TAG_END_SEPARATORS = "> \t\n\f/";

	private static final byte[] SCRIPT_TAG = Bytes.utf8("</script");

	private static final Map<String, Element> ELEMENT_NAME_MAP = Map.of("script", Element.SCRIPT, "style",
			Element.STYLE, "textarea", Element.TEXTAREA, "title", Element.TITLE, "meta", Element.META);

	private Transitions() {
	}

	/**
	 * Advances {@code c} across the literal text {@code s} (consuming a prefix of it).
	 * @param c the current context (may be mutated)
	 * @param s the literal text bytes
	 * @return the new context and bytes consumed
	 */
	static Result transition(Context c, byte[] s) {
		return switch (c.state) {
			case TEXT -> tText(c, s);
			case TAG -> tTag(c, s);
			case ATTR_NAME -> tAttrName(c, s);
			case AFTER_NAME -> tAfterName(c, s);
			case BEFORE_VALUE -> tBeforeValue(c, s);
			case HTML_CMT -> tHTMLCmt(c, s);
			case RCDATA -> tSpecialTagEnd(c, s);
			case ATTR -> tAttr(c, s);
			case URL, SRCSET -> tURL(c, s);
			case META_CONTENT -> tMetaContent(c, s);
			case META_CONTENT_URL -> tMetaContentURL(c, s);
			case JS -> tJS(c, s);
			case JS_DQ_STR, JS_SQ_STR, JS_REGEXP -> tJSDelimited(c, s);
			case JS_TMPL_LIT -> tJSTmpl(c, s);
			case JS_BLOCK_CMT, CSS_BLOCK_CMT -> tBlockCmt(c, s);
			case JS_LINE_CMT, JS_HTML_OPEN_CMT, JS_HTML_CLOSE_CMT, CSS_LINE_CMT -> tLineCmt(c, s);
			case CSS -> tCSS(c, s);
			case CSS_DQ_STR, CSS_SQ_STR, CSS_DQ_URL, CSS_SQ_URL, CSS_URL -> tCSSStr(c, s);
			case ERROR -> tError(c, s);
			default -> throw new IllegalStateException("no transition for state " + c.state);
		};
	}

	private static Result tText(Context c, byte[] s) {
		int k = 0;
		while (true) {
			int i = Bytes.indexByte(s, k, (byte) '<');
			if (i < 0 || i + 1 == s.length) {
				return new Result(c, s.length);
			}
			if (i + 4 <= s.length && Bytes.regionEquals(s, i, COMMENT_START)) {
				Context r = new Context();
				r.state = State.HTML_CMT;
				return new Result(r, i + 4);
			}
			i++;
			boolean end = false;
			if (s[i] == '/') {
				if (i + 1 == s.length) {
					return new Result(c, s.length);
				}
				end = true;
				i++;
			}
			EatTag tag = eatTagName(s, i);
			if (tag.end != i) {
				Element e = end ? Element.NONE : tag.element;
				Context r = new Context();
				r.state = State.TAG;
				r.element = e;
				return new Result(r, tag.end);
			}
			k = tag.end;
		}
	}

	private static Result tTag(Context c, byte[] s) {
		int i = eatWhiteSpace(s, 0);
		if (i == s.length) {
			return new Result(c, s.length);
		}
		if (s[i] == '>') {
			if (c.element == Element.META) {
				Context r = new Context();
				r.state = State.TEXT;
				return new Result(r, i + 1);
			}
			Context r = new Context();
			r.state = elementContentType(c.element);
			r.element = c.element;
			return new Result(r, i + 1);
		}
		EatAttr ea = eatAttrName(s, i);
		if (ea.err != null) {
			return errorResult(ea.err, s.length);
		}
		int j = ea.end;
		if (i == j) {
			EscapeError err = EscapeError.errorf(EscapeErrorCode.ERR_BAD_HTML, null, 0,
					"expected space, attr name, or end of tag, but got %s", quote(s, i, Math.min(s.length, i + 32)));
			return errorResult(err, s.length);
		}
		String attrName = Bytes.toLowerAscii(s, i, j);
		Attr attr = Attr.NONE;
		if (c.element == Element.SCRIPT && attrName.equals("type")) {
			attr = Attr.SCRIPT_TYPE;
		}
		else if (c.element == Element.META && attrName.equals("content")) {
			attr = Attr.META_CONTENT;
		}
		else {
			attr = switch (AttrTypes.attrType(attrName)) {
				case URL -> Attr.URL;
				case CSS -> Attr.STYLE;
				case JS -> Attr.SCRIPT;
				case SRCSET -> Attr.SRCSET;
				default -> Attr.NONE;
			};
		}
		Context r = new Context();
		r.state = (j == s.length) ? State.ATTR_NAME : State.AFTER_NAME;
		r.element = c.element;
		r.attr = attr;
		return new Result(r, j);
	}

	private static Result tAttrName(Context c, byte[] s) {
		EatAttr ea = eatAttrName(s, 0);
		if (ea.err != null) {
			return errorResult(ea.err, s.length);
		}
		if (ea.end != s.length) {
			c.state = State.AFTER_NAME;
		}
		return new Result(c, ea.end);
	}

	private static Result tAfterName(Context c, byte[] s) {
		int i = eatWhiteSpace(s, 0);
		if (i == s.length) {
			return new Result(c, s.length);
		}
		if (s[i] != '=') {
			// A valueless attribute followed by another attr or the tag end.
			c.state = State.TAG;
			return new Result(c, i);
		}
		c.state = State.BEFORE_VALUE;
		return new Result(c, i + 1);
	}

	private static Result tBeforeValue(Context c, byte[] s) {
		int i = eatWhiteSpace(s, 0);
		if (i == s.length) {
			return new Result(c, s.length);
		}
		Delim delim = Delim.SPACE_OR_TAG_END;
		if (s[i] == '\'') {
			delim = Delim.SINGLE_QUOTE;
			i++;
		}
		else if (s[i] == '"') {
			delim = Delim.DOUBLE_QUOTE;
			i++;
		}
		c.state = attrStartState(c.attr);
		c.delim = delim;
		return new Result(c, i);
	}

	private static Result tHTMLCmt(Context c, byte[] s) {
		int i = Bytes.index(s, 0, COMMENT_END);
		if (i != -1) {
			return new Result(new Context(), i + 3);
		}
		return new Result(c, s.length);
	}

	private static Result tSpecialTagEnd(Context c, byte[] s) {
		if (c.element != Element.NONE) {
			// "</script" within a script literal/comment is ignored so it can be escaped.
			if (c.element == Element.SCRIPT && (c.state.isInScriptLiteral() || c.state.isComment())) {
				return new Result(c, s.length);
			}
			int i = indexTagEnd(s, specialTagEndMarker(c.element));
			if (i != -1) {
				return new Result(new Context(), i);
			}
		}
		return new Result(c, s.length);
	}

	private static Result tAttr(Context c, byte[] s) {
		return new Result(c, s.length);
	}

	private static Result tURL(Context c, byte[] s) {
		if (Bytes.containsAny(s, 0, "#?")) {
			c.urlPart = UrlPart.QUERY_OR_FRAG;
		}
		else if (s.length != eatWhiteSpace(s, 0) && c.urlPart == UrlPart.NONE) {
			c.urlPart = UrlPart.PRE_QUERY;
		}
		return new Result(c, s.length);
	}

	private static Result tJS(Context c, byte[] s) {
		int i = Bytes.indexAny(s, 0, "\"`'/{}<-#");
		if (i == -1) {
			c.jsCtx = JsLex.nextJSCtx(s, s.length, c.jsCtx);
			return new Result(c, s.length);
		}
		c.jsCtx = JsLex.nextJSCtx(s, i, c.jsCtx);
		return stepJS(c, s, i);
	}

	private static Result stepJS(Context c, byte[] s, int start) {
		int i = start;
		switch (s[i]) {
			case '"' -> {
				c.state = State.JS_DQ_STR;
				c.jsCtx = JsCtx.REGEXP;
			}
			case '\'' -> {
				c.state = State.JS_SQ_STR;
				c.jsCtx = JsCtx.REGEXP;
			}
			case '`' -> {
				c.state = State.JS_TMPL_LIT;
				c.jsCtx = JsCtx.REGEXP;
			}
			case '/' -> {
				if (i + 1 < s.length && s[i + 1] == '/') {
					c.state = State.JS_LINE_CMT;
					i++;
				}
				else if (i + 1 < s.length && s[i + 1] == '*') {
					c.state = State.JS_BLOCK_CMT;
					i++;
				}
				else if (c.jsCtx == JsCtx.REGEXP) {
					c.state = State.JS_REGEXP;
				}
				else if (c.jsCtx == JsCtx.DIV_OP) {
					c.jsCtx = JsCtx.REGEXP;
				}
				else {
					EscapeError err = EscapeError.errorf(EscapeErrorCode.ERR_SLASH_AMBIG, null, 0,
							"'/' could start a division or regexp: %s", quote(s, i, Math.min(s.length, i + 32)));
					return errorResult(err, s.length);
				}
			}
			case '<' -> {
				if (i + 3 < s.length && Bytes.regionEquals(s, i, COMMENT_START)) {
					c.state = State.JS_HTML_OPEN_CMT;
					i += 3;
				}
			}
			case '-' -> {
				if (i + 2 < s.length && Bytes.regionEquals(s, i, COMMENT_END)) {
					c.state = State.JS_HTML_CLOSE_CMT;
					i += 2;
				}
			}
			case '#' -> {
				if (i + 1 < s.length && s[i + 1] == '!') {
					c.state = State.JS_LINE_CMT;
					i++;
				}
			}
			case '{' -> {
				if (c.jsBraceDepth == null || c.jsBraceDepth.isEmpty()) {
					return new Result(c, i + 1);
				}
				int last = c.jsBraceDepth.size() - 1;
				c.jsBraceDepth.set(last, c.jsBraceDepth.get(last) + 1);
			}
			case '}' -> {
				if (c.jsBraceDepth == null || c.jsBraceDepth.isEmpty()) {
					return new Result(c, i + 1);
				}
				int last = c.jsBraceDepth.size() - 1;
				int depth = c.jsBraceDepth.get(last) - 1;
				c.jsBraceDepth.set(last, depth);
				if (depth >= 0) {
					return new Result(c, i + 1);
				}
				c.jsBraceDepth.remove(last);
				c.state = State.JS_TMPL_LIT;
			}
			default -> throw new IllegalStateException("unreachable");
		}
		return new Result(c, i + 1);
	}

	private static Result tJSTmpl(Context c, byte[] s) {
		int k = 0;
		while (true) {
			int i = Bytes.indexAny(s, k, "`\\$");
			if (i == -1) {
				break;
			}
			switch (s[i]) {
				case '\\' -> {
					i++;
					if (i == s.length) {
						return errorResult(
								EscapeError.errorf(EscapeErrorCode.ERR_PARTIAL_ESCAPE, null, 0,
										"unfinished escape sequence in JS string: %s", quote(s, 0, s.length)),
								s.length);
					}
				}
				case '$' -> {
					if (s.length >= i + 2 && s[i + 1] == '{') {
						if (c.jsBraceDepth == null) {
							c.jsBraceDepth = new ArrayList<>();
						}
						c.jsBraceDepth.add(0);
						c.state = State.JS;
						return new Result(c, i + 2);
					}
				}
				case '`' -> {
					c.state = State.JS;
					return new Result(c, i + 1);
				}
				default -> {
				}
			}
			k = i + 1;
		}
		return new Result(c, s.length);
	}

	private static Result tJSDelimited(Context c, byte[] s) {
		String specials = "\\\"";
		if (c.state == State.JS_SQ_STR) {
			specials = "\\'";
		}
		else if (c.state == State.JS_REGEXP) {
			specials = "\\/[]";
		}
		boolean inCharset = false;
		int k = 0;
		while (true) {
			int i = Bytes.indexAny(s, k, specials);
			if (i == -1) {
				break;
			}
			switch (s[i]) {
				case '\\' -> {
					i++;
					if (i == s.length) {
						return errorResult(
								EscapeError.errorf(EscapeErrorCode.ERR_PARTIAL_ESCAPE, null, 0,
										"unfinished escape sequence in JS string: %s", quote(s, 0, s.length)),
								s.length);
					}
				}
				case '[' -> {
					inCharset = true;
				}
				case ']' -> {
					inCharset = false;
				}
				case '/' -> {
					// "</script" in a regex literal: '/' does not close it.
					if (i > 0 && Bytes.regionEqualsFold(s, i - 1, SCRIPT_TAG)) {
						i++;
					}
					else if (!inCharset) {
						c.state = State.JS;
						c.jsCtx = JsCtx.DIV_OP;
						return new Result(c, i + 1);
					}
				}
				default -> {
					if (!inCharset) {
						c.state = State.JS;
						c.jsCtx = JsCtx.DIV_OP;
						return new Result(c, i + 1);
					}
				}
			}
			k = i + 1;
		}
		if (inCharset) {
			return errorResult(EscapeError.errorf(EscapeErrorCode.ERR_PARTIAL_CHARSET, null, 0,
					"unfinished JS regexp charset: %s", quote(s, 0, s.length)), s.length);
		}
		return new Result(c, s.length);
	}

	private static Result tBlockCmt(Context c, byte[] s) {
		int i = Bytes.index(s, 0, BLOCK_COMMENT_END);
		if (i == -1) {
			return new Result(c, s.length);
		}
		switch (c.state) {
			case JS_BLOCK_CMT -> {
				c.state = State.JS;
			}
			case CSS_BLOCK_CMT -> {
				c.state = State.CSS;
			}
			default -> throw new IllegalStateException(c.state.toString());
		}
		return new Result(c, i + 2);
	}

	private static Result tLineCmt(Context c, byte[] s) {
		State endState;
		int i;
		switch (c.state) {
			case JS_LINE_CMT, JS_HTML_OPEN_CMT, JS_HTML_CLOSE_CMT -> {
				endState = State.JS;
				i = indexJsLineEnd(s);
			}
			case CSS_LINE_CMT -> {
				endState = State.CSS;
				i = Bytes.indexAny(s, 0, "\n\f\r");
			}
			default -> throw new IllegalStateException(c.state.toString());
		}
		if (i == -1) {
			return new Result(c, s.length);
		}
		c.state = endState;
		// The line terminator is recognised separately, so it is not consumed here.
		return new Result(c, i);
	}

	private static Result tCSS(Context c, byte[] s) {
		int k = 0;
		while (true) {
			int i = Bytes.indexAny(s, k, "(\"'/");
			if (i == -1) {
				return new Result(c, s.length);
			}
			switch (s[i]) {
				case '(' -> {
					int pEnd = trimRightWc(s, i);
					if (CssLex.endsWithCSSKeyword(s, pEnd, "url")) {
						int j = skipWcLeft(s, i + 1);
						if (j != s.length && s[j] == '"') {
							c.state = State.CSS_DQ_URL;
							j++;
						}
						else if (j != s.length && s[j] == '\'') {
							c.state = State.CSS_SQ_URL;
							j++;
						}
						else {
							c.state = State.CSS_URL;
						}
						return new Result(c, j);
					}
				}
				case '/' -> {
					if (i + 1 < s.length) {
						if (s[i + 1] == '/') {
							c.state = State.CSS_LINE_CMT;
							return new Result(c, i + 2);
						}
						if (s[i + 1] == '*') {
							c.state = State.CSS_BLOCK_CMT;
							return new Result(c, i + 2);
						}
					}
				}
				case '"' -> {
					c.state = State.CSS_DQ_STR;
					return new Result(c, i + 1);
				}
				case '\'' -> {
					c.state = State.CSS_SQ_STR;
					return new Result(c, i + 1);
				}
				default -> {
				}
			}
			k = i + 1;
		}
	}

	private static Result tCSSStr(Context c, byte[] s) {
		String endAndEsc = switch (c.state) {
			case CSS_DQ_STR, CSS_DQ_URL -> "\\\"";
			case CSS_SQ_STR, CSS_SQ_URL -> "\\'";
			case CSS_URL -> "\\\t\n\f\r )";
			default -> throw new IllegalStateException(c.state.toString());
		};
		int k = 0;
		while (true) {
			int i = Bytes.indexAny(s, k, endAndEsc);
			if (i == -1) {
				Result r = tURL(c, CssLex.decodeCSS(Arrays.copyOfRange(s, k, s.length)));
				return new Result(r.context, k + r.consumed);
			}
			if (s[i] == '\\') {
				i++;
				if (i == s.length) {
					return errorResult(EscapeError.errorf(EscapeErrorCode.ERR_PARTIAL_ESCAPE, null, 0,
							"unfinished escape sequence in CSS string: %s", quote(s, 0, s.length)), s.length);
				}
			}
			else {
				c.state = State.CSS;
				return new Result(c, i + 1);
			}
			Result r = tURL(c, CssLex.decodeCSS(Arrays.copyOfRange(s, 0, i + 1)));
			c = r.context;
			k = i + 1;
		}
	}

	private static Result tError(Context c, byte[] s) {
		return new Result(c, s.length);
	}

	private static Result tMetaContent(Context c, byte[] s) {
		byte[] url = Bytes.utf8("url");
		for (int i = 0; i < s.length; i++) {
			if (i + 3 <= s.length - 1 && Bytes.regionEqualsFold(s, i, url)) {
				int j = eatWhiteSpace(s, i + 3);
				if (j < s.length && s[j] == '=') {
					c.state = State.META_CONTENT_URL;
					return new Result(c, j + 1);
				}
			}
		}
		return new Result(c, s.length);
	}

	private static Result tMetaContentURL(Context c, byte[] s) {
		for (int i = 0; i < s.length; i++) {
			if (s[i] == ';') {
				c.state = State.META_CONTENT;
				return new Result(c, i + 1);
			}
		}
		return new Result(c, s.length);
	}

	// indexTagEnd finds the case-insensitive special tag end "</tag<sep>", returning the
	// absolute index of "</", or -1.
	private static int indexTagEnd(byte[] s, byte[] tag) {
		int from = 0;
		while (from < s.length) {
			int absI = Bytes.index(s, from, SPECIAL_TAG_END_PREFIX);
			if (absI == -1) {
				return -1;
			}
			int afterPrefix = absI + SPECIAL_TAG_END_PREFIX.length;
			if (tag.length <= s.length - afterPrefix && Bytes.regionEqualsFold(s, afterPrefix, tag)) {
				int afterTag = afterPrefix + tag.length;
				if (afterTag < s.length && TAG_END_SEPARATORS.indexOf(s[afterTag] & 0xff) != -1) {
					return absI;
				}
				from = afterTag;
			}
			else {
				from = afterPrefix;
			}
		}
		return -1;
	}

	private static EatTag eatTagName(byte[] s, int i) {
		if (i == s.length || !asciiAlpha(s[i] & 0xff)) {
			return new EatTag(i, Element.NONE);
		}
		int j = i + 1;
		while (j < s.length) {
			int x = s[j] & 0xff;
			if (asciiAlphaNum(x)) {
				j++;
			}
			else if ((x == ':' || x == '-') && j + 1 < s.length && asciiAlphaNum(s[j + 1] & 0xff)) {
				j += 2;
			}
			else {
				break;
			}
		}
		Element e = ELEMENT_NAME_MAP.getOrDefault(Bytes.toLowerAscii(s, i, j), Element.NONE);
		return new EatTag(j, e);
	}

	private static EatAttr eatAttrName(byte[] s, int i) {
		for (int j = i; j < s.length; j++) {
			switch (s[j]) {
				case ' ', '\t', '\n', '\f', '\r', '=', '>' -> {
					return new EatAttr(j, null);
				}
				case '\'', '"', '<' -> {
					EscapeError err = EscapeError.errorf(EscapeErrorCode.ERR_BAD_HTML, null, 0,
							"%s in attribute name: %s", quote(s, j, j + 1), quote(s, i, Math.min(s.length, i + 32)));
					return new EatAttr(-1, err);
				}
				default -> {
				}
			}
		}
		return new EatAttr(s.length, null);
	}

	private static int eatWhiteSpace(byte[] s, int i) {
		for (int j = i; j < s.length; j++) {
			switch (s[j]) {
				case ' ', '\t', '\n', '\f', '\r' -> {
				}
				default -> {
					return j;
				}
			}
		}
		return s.length;
	}

	private static int indexJsLineEnd(byte[] s) {
		for (int i = 0; i < s.length; i++) {
			int b = s[i] & 0xff;
			if (b == '\n' || b == '\r') {
				return i;
			}
			// U+2028 (E2 80 A8) and U+2029 (E2 80 A9) are JS line terminators.
			if (b == 0xe2 && i + 2 < s.length && (s[i + 1] & 0xff) == 0x80
					&& ((s[i + 2] & 0xff) == 0xa8 || (s[i + 2] & 0xff) == 0xa9)) {
				return i;
			}
		}
		return -1;
	}

	private static int trimRightWc(byte[] s, int end) {
		int j = end;
		while (j > 0 && isWc(s[j - 1])) {
			j--;
		}
		return j;
	}

	private static int skipWcLeft(byte[] s, int from) {
		int j = from;
		while (j < s.length && isWc(s[j])) {
			j++;
		}
		return j;
	}

	private static boolean isWc(byte b) {
		return switch (b) {
			case '\t', '\n', '\f', '\r', ' ' -> true;
			default -> false;
		};
	}

	private static boolean asciiAlpha(int c) {
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
	}

	private static boolean asciiAlphaNum(int c) {
		return asciiAlpha(c) || (c >= '0' && c <= '9');
	}

	private static State elementContentType(Element element) {
		return switch (element) {
			case SCRIPT -> State.JS;
			case STYLE -> State.CSS;
			case TEXTAREA, TITLE -> State.RCDATA;
			default -> State.TEXT;
		};
	}

	private static State attrStartState(Attr attr) {
		return switch (attr) {
			case SCRIPT -> State.JS;
			case STYLE -> State.CSS;
			case URL -> State.URL;
			case SRCSET -> State.SRCSET;
			case META_CONTENT -> State.META_CONTENT;
			default -> State.ATTR;
		};
	}

	private static byte[] specialTagEndMarker(Element element) {
		return switch (element) {
			case SCRIPT -> Bytes.utf8("script");
			case STYLE -> Bytes.utf8("style");
			case TEXTAREA -> Bytes.utf8("textarea");
			case TITLE -> Bytes.utf8("title");
			default -> Bytes.utf8("");
		};
	}

	private static Result errorResult(EscapeError err, int consumed) {
		Context r = new Context();
		r.state = State.ERROR;
		r.err = err;
		return new Result(r, consumed);
	}

	// A best-effort %q-style quote of s[from..to] for error messages.
	private static String quote(byte[] s, int from, int to) {
		return '"' + new String(s, from, Math.max(0, to - from), java.nio.charset.StandardCharsets.UTF_8) + '"';
	}

	/**
	 * The result of a transition: the new context and the number of bytes consumed from
	 * the front of the input.
	 *
	 * @param context the resulting context
	 * @param consumed the number of bytes consumed
	 */
	record Result(Context context, int consumed) {
	}

	private record EatTag(int end, Element element) {
	}

	private record EatAttr(int end, EscapeError err) {
	}

}
