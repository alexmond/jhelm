package org.alexmond.gotmpl4j.html;

import java.util.ArrayList;
import java.util.List;

import org.alexmond.gotmpl4j.parse.Node;

/**
 * Describes the state an HTML parser must be in when it reaches the output of a
 * particular template node. Mirrors Go {@code html/template}'s {@code context}
 * (context.go).
 *
 * <p>
 * The zero value (default constructor) is the start context — {@link State#TEXT} with
 * every other field at its first/none value — for a template producing an HTML fragment.
 * The escaper threads a {@code Context} through the parse tree, advancing it across
 * literal text (the transition machine) and using it to pick an escaper for each action.
 *
 * <p>
 * Fields are mutable and package-private; the transition machine works on
 * {@link #copy()}d copies (Go passes {@code context} by value), so callers must not share
 * a single instance across branches without copying.
 */
public final class Context {

	State state = State.TEXT;

	Delim delim = Delim.NONE;

	UrlPart urlPart = UrlPart.NONE;

	JsCtx jsCtx = JsCtx.REGEXP;

	/**
	 * For each open JS template-literal interpolation, the depth of braces seen, so the
	 * next {@code }} can be matched to its interpolation. {@code null} when not in one
	 * (mirrors Go's nil slice).
	 */
	List<Integer> jsBraceDepth;

	Attr attr = Attr.NONE;

	Element element = Element.NONE;

	/** The {@code range} node, for break/continue handling. */
	Node n;

	/** Set (with {@link State#ERROR}) once escaping has failed. */
	EscapeError err;

	/**
	 * The HTML-parser state.
	 * @return the state
	 */
	public State state() {
		return this.state;
	}

	/**
	 * The current attribute delimiter.
	 * @return the delimiter
	 */
	public Delim delim() {
		return this.delim;
	}

	/**
	 * The error that ended escaping, if any.
	 * @return the escape error, or {@code null}
	 */
	public EscapeError err() {
		return this.err;
	}

	/**
	 * Returns a deep copy of this context (the {@code jsBraceDepth} list is cloned).
	 * @return a copy
	 */
	public Context copy() {
		Context copy = new Context();
		copy.state = this.state;
		copy.delim = this.delim;
		copy.urlPart = this.urlPart;
		copy.jsCtx = this.jsCtx;
		copy.jsBraceDepth = (this.jsBraceDepth != null) ? new ArrayList<>(this.jsBraceDepth) : null;
		copy.attr = this.attr;
		copy.element = this.element;
		copy.n = this.n;
		copy.err = this.err;
		return copy;
	}

	/**
	 * Reports whether two contexts are equal (the {@code err} is compared by identity, as
	 * in Go; a {@code null} and an empty {@code jsBraceDepth} are considered equal).
	 * @param d the other context
	 * @return {@code true} if equal
	 */
	public boolean eq(Context d) {
		return this.state == d.state && this.delim == d.delim && this.urlPart == d.urlPart && this.jsCtx == d.jsCtx
				&& jsBraceDepthEquals(this.jsBraceDepth, d.jsBraceDepth) && this.attr == d.attr
				&& this.element == d.element && this.err == d.err;
	}

	/**
	 * Produces a template-name suffix that distinguishes a template escaped in this
	 * context from one escaped in a different context (mirrors Go's {@code mangle}). The
	 * default ({@link State#TEXT}) context yields the input name unchanged.
	 * @param templateName the base template name
	 * @return the mangled name
	 */
	public String mangle(String templateName) {
		if (this.state == State.TEXT) {
			return templateName;
		}
		StringBuilder s = new StringBuilder(templateName).append("$htmltemplate_").append(this.state);
		if (this.delim != Delim.NONE) {
			s.append('_').append(this.delim);
		}
		if (this.urlPart != UrlPart.NONE) {
			s.append('_').append(this.urlPart);
		}
		if (this.jsCtx != JsCtx.REGEXP) {
			s.append('_').append(this.jsCtx);
		}
		if (this.jsBraceDepth != null) {
			s.append("_jsBraceDepth(").append(this.jsBraceDepth).append(')');
		}
		if (this.attr != Attr.NONE) {
			s.append('_').append(this.attr);
		}
		if (this.element != Element.NONE) {
			s.append('_').append(this.element);
		}
		return s.toString();
	}

	@Override
	public String toString() {
		return "{" + this.state + " " + this.delim + " " + this.urlPart + " " + this.jsCtx + " " + this.jsBraceDepth
				+ " " + this.attr + " " + this.element + " " + this.err + "}";
	}

	private static boolean jsBraceDepthEquals(List<Integer> a, List<Integer> b) {
		int sizeA = (a != null) ? a.size() : 0;
		int sizeB = (b != null) ? b.size() : 0;
		if (sizeA != sizeB) {
			return false;
		}
		for (int i = 0; i < sizeA; i++) {
			if (!a.get(i).equals(b.get(i))) {
				return false;
			}
		}
		return true;
	}

}
