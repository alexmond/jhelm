package org.alexmond.gotmpl4j.html;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import org.alexmond.gotmpl4j.parse.ActionNode;
import org.alexmond.gotmpl4j.parse.BranchNode;
import org.alexmond.gotmpl4j.parse.BreakNode;
import org.alexmond.gotmpl4j.parse.CommandNode;
import org.alexmond.gotmpl4j.parse.CommentNode;
import org.alexmond.gotmpl4j.parse.ContinueNode;
import org.alexmond.gotmpl4j.parse.IdentifierNode;
import org.alexmond.gotmpl4j.parse.IfNode;
import org.alexmond.gotmpl4j.parse.ListNode;
import org.alexmond.gotmpl4j.parse.Node;
import org.alexmond.gotmpl4j.parse.PipeNode;
import org.alexmond.gotmpl4j.parse.RangeNode;
import org.alexmond.gotmpl4j.parse.TemplateNode;
import org.alexmond.gotmpl4j.parse.TextNode;
import org.alexmond.gotmpl4j.parse.WithNode;

/**
 * The contextual auto-escaping pass, ported from Go {@code html/template}'s escape.go. It
 * walks a parsed template set, threads an HTML/JS/CSS {@link Context} through the nodes
 * via the {@link Transitions} machine, and appends the right {@link Escapers} to each
 * action's pipeline so output is safe in the context it appears in.
 *
 * <p>
 * Like Go, edits are deferred (collected per node, applied in {@link #commit()}) so a
 * template analyzed in multiple passes — range-loop re-entry, recursion fixpoint — is
 * only mutated once the analysis is accepted.
 *
 * <p>
 * <strong>Limitation vs Go:</strong> this pass does not yet clone a template called from
 * two <em>different</em> contexts; such use is reported as an error (Go would derive a
 * per-context copy). Single-context calls (including the common text-context
 * {@code {{template}}}) and recursion are supported.
 */
public final class Escaper {

	private static final byte[] LT_BYTES = Bytes.utf8("&lt;");

	private static final byte[] DOCTYPE = Bytes.utf8("<!DOCTYPE");

	private static final Pattern SPECIAL_SCRIPT_TAG = Pattern.compile("(?i)<(script|/script|!--)");

	private static final Set<String> PREDEFINED_ESCAPERS = Set.of("html", "urlquery");

	private static final Map<String, String> EQUIV_ESCAPERS = Map.of("_html_template_attrescaper", "html",
			"_html_template_htmlescaper", "html", "_html_template_rcdataescaper", "html", "_html_template_urlescaper",
			"urlquery", "_html_template_urlnormalizer", "urlquery");

	private static final Map<String, Set<String>> REDUNDANT_FUNCS = Map.of("_html_template_commentescaper",
			Set.of("_html_template_attrescaper", "_html_template_htmlescaper"), "_html_template_cssescaper",
			Set.of("_html_template_attrescaper"), "_html_template_jsregexpescaper",
			Set.of("_html_template_attrescaper"), "_html_template_jsstrescaper", Set.of("_html_template_attrescaper"),
			"_html_template_jstmpllitescaper", Set.of("_html_template_attrescaper"), "_html_template_urlescaper",
			Set.of("_html_template_urlnormalizer"));

	private final Map<String, Node> templates;

	private final Map<String, Context> output = new HashMap<>();

	private final Set<String> called = new HashSet<>();

	private final Map<ActionNode, List<String>> actionNodeEdits = new IdentityHashMap<>();

	private final Map<TextNode, String> textNodeEdits = new IdentityHashMap<>();

	private RangeContext rangeContext;

	private Escaper(Map<String, Node> templates) {
		this.templates = templates;
	}

	/**
	 * Rewrites the named template (and the templates it reaches) so all output is
	 * contextually escaped. Mutates the nodes in {@code templates} in place.
	 * @param templates the template set (rootNodes)
	 * @param name the entry template name
	 * @throws EscapeError if the template cannot be safely escaped
	 */
	public static void escape(Map<String, Node> templates, String name) {
		Escaper e = new Escaper(templates);
		Tree res = e.escapeTree(new Context(), templates.get(name), name, 0);
		Context c = res.context();
		if (c.err != null) {
			EscapeError ce = c.err;
			throw new EscapeError(ce.code(), ce.node(), name, ce.getLine(), ce.description());
		}
		if (c.state != State.TEXT) {
			throw new EscapeError(EscapeErrorCode.ERR_END_CONTEXT, null, name, 0, "ends in a non-text context: " + c);
		}
		e.commit();
	}

	private Context escapeNode(Context c, Node n) {
		if (n instanceof ActionNode a) {
			return escapeAction(c, a);
		}
		if (n instanceof BreakNode bn) {
			c.n = bn;
			this.rangeContext.breaks.add(c);
			return dead();
		}
		if (n instanceof CommentNode) {
			return c;
		}
		if (n instanceof ContinueNode cn) {
			c.n = cn;
			this.rangeContext.continues.add(c);
			return dead();
		}
		if (n instanceof IfNode in) {
			return escapeBranch(c, in, "if");
		}
		if (n instanceof ListNode ln) {
			return escapeList(c, ln);
		}
		if (n instanceof RangeNode rn) {
			return escapeBranch(c, rn, "range");
		}
		if (n instanceof TemplateNode tn) {
			return escapeTemplate(c, tn);
		}
		if (n instanceof TextNode txt) {
			return escapeText(c, txt);
		}
		if (n instanceof WithNode wn) {
			return escapeBranch(c, wn, "with");
		}
		throw new IllegalStateException("escaping " + n + " is unimplemented");
	}

	private Context escapeAction(Context c, ActionNode n) {
		PipeNode pipe = n.getPipeNode();
		if (pipe.getVariableCount() != 0) {
			// A local variable assignment, not an interpolation.
			return c;
		}
		c = nudge(c);
		Context predefErr = checkPredefinedEscapers(c, n, pipe);
		if (predefErr != null) {
			return predefErr;
		}
		List<String> s = new ArrayList<>(3);
		Context err = chooseEscapers(c, n, s);
		if (err != null) {
			return err;
		}
		switch (c.delim) {
			case NONE -> {
			}
			case SPACE_OR_TAG_END -> s.add("_html_template_nospaceescaper");
			default -> s.add("_html_template_attrescaper");
		}
		editActionNode(n, s);
		return c;
	}

	// Appends the content escapers for c.state into s; returns an error context or null.
	private Context chooseEscapers(Context c, ActionNode n, List<String> s) {
		switch (c.state) {
			case ERROR -> {
				return c;
			}
			case URL, CSS_DQ_STR, CSS_SQ_STR, CSS_DQ_URL, CSS_SQ_URL, CSS_URL -> {
				return urlEscapers(c, n, s);
			}
			case META_CONTENT, ATTR -> {
				// Handled by the delim check.
			}
			case META_CONTENT_URL -> s.add("_html_template_urlfilter");
			case JS -> {
				s.add("_html_template_jsvalescaper");
				// A slash after a value starts a div operator.
				c.jsCtx = JsCtx.DIV_OP;
			}
			case JS_DQ_STR, JS_SQ_STR -> s.add("_html_template_jsstrescaper");
			case JS_TMPL_LIT -> s.add("_html_template_jstmpllitescaper");
			case JS_REGEXP -> s.add("_html_template_jsregexpescaper");
			case CSS -> s.add("_html_template_cssvaluefilter");
			case TEXT -> s.add("_html_template_htmlescaper");
			case RCDATA -> s.add("_html_template_rcdataescaper");
			case ATTR_NAME, TAG -> {
				c.state = State.ATTR_NAME;
				s.add("_html_template_htmlnamefilter");
			}
			case SRCSET -> s.add("_html_template_srcsetescaper");
			default -> {
				if (c.state.isComment()) {
					s.add("_html_template_commentescaper");
				}
				else {
					throw new IllegalStateException("unexpected state " + c.state);
				}
			}
		}
		return null;
	}

	private Context urlEscapers(Context c, ActionNode n, List<String> s) {
		switch (c.urlPart) {
			case NONE -> {
				s.add("_html_template_urlfilter");
				addUrlPreQuery(c, s);
			}
			case PRE_QUERY -> addUrlPreQuery(c, s);
			case QUERY_OR_FRAG -> s.add("_html_template_urlescaper");
			case UNKNOWN -> {
				return errCtx(EscapeErrorCode.ERR_AMBIG_CONTEXT, n, 0,
						n + " appears in an ambiguous context within a URL");
			}
		}
		return null;
	}

	private void addUrlPreQuery(Context c, List<String> s) {
		if (c.state == State.CSS_DQ_STR || c.state == State.CSS_SQ_STR) {
			s.add("_html_template_cssescaper");
		}
		else {
			s.add("_html_template_urlnormalizer");
		}
	}

	private Context checkPredefinedEscapers(Context c, ActionNode n, PipeNode pipe) {
		List<CommandNode> cmds = pipe.getCommands();
		for (int pos = 0; pos < cmds.size(); pos++) {
			Node first = cmds.get(pos).getFirstArgument();
			if (!(first instanceof IdentifierNode id)) {
				continue;
			}
			String ident = id.getIdentifier();
			if (PREDEFINED_ESCAPERS.contains(ident)) {
				boolean lastInPipe = pos == cmds.size() - 1;
				boolean badHtmlInUnquoted = c.state == State.ATTR && c.delim == Delim.SPACE_OR_TAG_END
						&& ident.equals("html");
				if (!lastInPipe || badHtmlInUnquoted) {
					return errCtx(EscapeErrorCode.ERR_PREDEFINED_ESCAPER, n, 0,
							"predefined escaper \"" + ident + "\" disallowed in template");
				}
			}
		}
		return null;
	}

	private Context escapeBranch(Context c, BranchNode n, String nodeName) {
		boolean isRange = nodeName.equals("range");
		if (isRange) {
			this.rangeContext = new RangeContext(this.rangeContext);
		}
		Context c0 = escapeList(c.copy(), n.getIfListNode());
		if (isRange) {
			if (c0.state != State.ERROR) {
				c0 = joinRange(c0, this.rangeContext);
			}
			this.rangeContext = this.rangeContext.outer;
			if (c0.state == State.ERROR) {
				return c0;
			}
			// The body of a range can run more than once: check that running it twice
			// yields the same context as running it once.
			this.rangeContext = new RangeContext(this.rangeContext);
			Body b = escapeListConditionally(c0, n.getIfListNode(), null);
			c0 = join(c0, b.context(), n, nodeName);
			if (c0.state == State.ERROR) {
				this.rangeContext = this.rangeContext.outer;
				c0.err = new EscapeError(c0.err.code(), n, c0.err.templateName(), c0.err.getLine(),
						"on range loop re-entry: " + c0.err.description());
				return c0;
			}
			c0 = joinRange(c0, this.rangeContext);
			this.rangeContext = this.rangeContext.outer;
			if (c0.state == State.ERROR) {
				return c0;
			}
		}
		Context c1 = escapeList(c.copy(), n.getElseListNode());
		return join(c0, c1, n, nodeName);
	}

	private Context joinRange(Context c0, RangeContext rc) {
		for (Context cb : rc.breaks) {
			c0 = join(c0, cb, cb.n, "range");
			if (c0.state == State.ERROR) {
				return c0;
			}
		}
		for (Context cc : rc.continues) {
			c0 = join(c0, cc, cc.n, "range");
			if (c0.state == State.ERROR) {
				return c0;
			}
		}
		return c0;
	}

	private Context escapeList(Context c, ListNode n) {
		if (n == null) {
			return c;
		}
		for (Node m : n) {
			c = escapeNode(c, m);
			if (c.state == State.DEAD) {
				break;
			}
		}
		return c;
	}

	private Body escapeListConditionally(Context c, ListNode n, BiPredicate<Escaper, Context> filter) {
		Escaper e1 = new Escaper(this.templates);
		e1.rangeContext = this.rangeContext;
		e1.output.putAll(this.output);
		Context c1 = e1.escapeList(c, n);
		boolean ok = filter != null && filter.test(e1, c1);
		if (ok) {
			this.output.putAll(e1.output);
			this.called.addAll(e1.called);
			for (Map.Entry<ActionNode, List<String>> en : e1.actionNodeEdits.entrySet()) {
				editActionNode(en.getKey(), en.getValue());
			}
			for (Map.Entry<TextNode, String> en : e1.textNodeEdits.entrySet()) {
				editTextNode(en.getKey(), en.getValue());
			}
		}
		return new Body(c1, ok);
	}

	private Context escapeTemplate(Context c, TemplateNode n) {
		Tree t = escapeTree(c, n, n.getName(), 0);
		// No cloning yet: the call is not retargeted, so a template reached in two
		// contexts
		// triggers a "node shared" error via the double-edit guard rather than being
		// cloned.
		return t.context();
	}

	private Tree escapeTree(Context c, Node node, String name, int line) {
		String dname = c.mangle(name);
		this.called.add(dname);
		Context cached = this.output.get(dname);
		if (cached != null) {
			return new Tree(cached, dname);
		}
		Node t = this.templates.get(name);
		if (!(t instanceof ListNode root)) {
			return new Tree(
					errCtx(EscapeErrorCode.ERR_NO_SUCH_TEMPLATE, node, line, "no such template \"" + name + "\""),
					dname);
		}
		return new Tree(computeOutCtx(c, dname, root), dname);
	}

	private Context computeOutCtx(Context c, String dname, ListNode root) {
		Body b1 = escapeTemplateBody(c, dname, root);
		Context c1 = b1.context();
		boolean ok = b1.ok();
		if (!ok) {
			Body b2 = escapeTemplateBody(c1, dname, root);
			if (b2.ok()) {
				c1 = b2.context();
				ok = true;
			}
		}
		if (!ok && c1.state != State.ERROR) {
			return errCtx(EscapeErrorCode.ERR_OUTPUT_CONTEXT, root, 0,
					"cannot compute output context for template " + dname);
		}
		return c1;
	}

	private Body escapeTemplateBody(Context c, String dname, ListNode root) {
		BiPredicate<Escaper, Context> filter = (e1, c1) -> {
			if (c1.state == State.ERROR) {
				return false;
			}
			if (!e1.called.contains(dname)) {
				return true;
			}
			return c.eq(c1);
		};
		// Assume the output context equals the input so recursive calls terminate.
		this.output.put(dname, c);
		return escapeListConditionally(c, root, filter);
	}

	private Context escapeText(Context c, TextNode n) {
		byte[] s = n.getText().getBytes(StandardCharsets.UTF_8);
		int written = 0;
		int i = 0;
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		while (i != s.length) {
			Transitions.Result tr = contextAfterText(c, Arrays.copyOfRange(s, i, s.length));
			Context c1 = tr.context();
			int i1 = i + tr.consumed();
			if (c.state == State.TEXT || c.state == State.RCDATA) {
				written = escapeStrayLt(s, written, i, i1, c, c1, b);
			}
			else if (c.state.isComment() && c.delim == Delim.NONE) {
				written = normalizeComment(s, written, i1, c, b);
			}
			written = preserveCommentStart(s, written, i1, c, c1, b);
			written = escapeScriptTags(s, written, i, i1, c, b);
			if (i == i1 && c.state == c1.state) {
				throw new IllegalStateException("infinite loop in escapeText");
			}
			c = c1;
			i = i1;
		}
		if (written != 0 && c.state != State.ERROR) {
			if (!c.state.isComment() || c.delim != Delim.NONE) {
				b.write(s, written, s.length - written);
			}
			editTextNode(n, b.toString(StandardCharsets.UTF_8));
		}
		return c;
	}

	private int escapeStrayLt(byte[] s, int written, int i, int i1, Context c, Context c1, ByteArrayOutputStream b) {
		int end = i1;
		if (c1.state != c.state) {
			for (int j = end - 1; j >= i; j--) {
				if (s[j] == '<') {
					end = j;
					break;
				}
			}
		}
		for (int j = i; j < end; j++) {
			if (s[j] == '<' && !Bytes.regionEqualsFold(s, j, DOCTYPE)) {
				b.write(s, written, j - written);
				b.write(LT_BYTES, 0, LT_BYTES.length);
				written = j + 1;
			}
		}
		return written;
	}

	private int normalizeComment(byte[] s, int written, int i1, Context c, ByteArrayOutputStream b) {
		switch (c.state) {
			case JS_BLOCK_CMT -> b.write(containsNewline(s, written, i1) ? '\n' : ' ');
			case CSS_BLOCK_CMT -> b.write(' ');
			default -> {
			}
		}
		return i1;
	}

	private int preserveCommentStart(byte[] s, int written, int i1, Context c, Context c1, ByteArrayOutputStream b) {
		if (c.state != c1.state && c1.state.isComment() && c1.delim == Delim.NONE) {
			int cs = i1 - 2;
			if (c1.state == State.HTML_CMT || c1.state == State.JS_HTML_OPEN_CMT) {
				cs -= 2;
			}
			else if (c1.state == State.JS_HTML_CLOSE_CMT) {
				cs -= 1;
			}
			b.write(s, written, cs - written);
			return i1;
		}
		return written;
	}

	private int escapeScriptTags(byte[] s, int written, int i, int i1, Context c, ByteArrayOutputStream b) {
		if (c.state.isInScriptLiteral()) {
			String slice = new String(s, i, i1 - i, StandardCharsets.UTF_8);
			if (SPECIAL_SCRIPT_TAG.matcher(slice).find()) {
				b.write(s, written, i - written);
				byte[] esc = SPECIAL_SCRIPT_TAG.matcher(slice).replaceAll("\\\\x3C$1").getBytes(StandardCharsets.UTF_8);
				b.write(esc, 0, esc.length);
				return i1;
			}
		}
		return written;
	}

	private Transitions.Result contextAfterText(Context c, byte[] s) {
		if (c.delim == Delim.NONE) {
			Transitions.Result r = Transitions.specialTagEnd(c, s);
			if (r.consumed() == 0) {
				return new Transitions.Result(r.context(), 0);
			}
			return Transitions.transitionFor(c.state, c, Arrays.copyOfRange(s, 0, r.consumed()));
		}
		int i = Bytes.indexAny(s, 0, delimEnds(c.delim));
		if (i == -1) {
			i = s.length;
		}
		if (c.delim == Delim.SPACE_OR_TAG_END) {
			int j = Bytes.indexAny(s, 0, "\"'<=`");
			if (j >= 0 && j < i) {
				Context err = errCtx(EscapeErrorCode.ERR_BAD_HTML, null, 0,
						"unexpected character in unquoted attribute");
				return new Transitions.Result(err, s.length);
			}
		}
		if (i == s.length) {
			// Remain inside the attribute; decode entities so non-HTML rules see token
			// boundaries (e.g. onclick="alert(&quot;Hi!&quot;)").
			byte[] u = HtmlUnescape.unescape(new String(s, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
			Context cc = c;
			int off = 0;
			while (off < u.length) {
				Transitions.Result r = Transitions.transitionFor(cc.state, cc, Arrays.copyOfRange(u, off, u.length));
				cc = r.context();
				if (r.consumed() == 0) {
					break;
				}
				off += r.consumed();
			}
			return new Transitions.Result(cc, s.length);
		}
		Element element = c.element;
		if (c.state == State.ATTR && c.element == Element.SCRIPT && c.attr == Attr.SCRIPT_TYPE
				&& !JsLex.isJsType(new String(s, 0, i, StandardCharsets.UTF_8))) {
			element = Element.NONE;
		}
		int consumed = (c.delim != Delim.SPACE_OR_TAG_END) ? i + 1 : i;
		Context out = new Context();
		out.state = State.TAG;
		out.element = element;
		return new Transitions.Result(out, consumed);
	}

	private void editActionNode(ActionNode n, List<String> cmds) {
		if (this.actionNodeEdits.containsKey(n)) {
			throw new EscapeError(EscapeErrorCode.ERR_BRANCH_END, n, "", 0,
					"action node shared between templates (multi-context template use is not supported)");
		}
		this.actionNodeEdits.put(n, cmds);
	}

	private void editTextNode(TextNode n, String text) {
		if (this.textNodeEdits.containsKey(n)) {
			throw new EscapeError(EscapeErrorCode.ERR_BRANCH_END, n, "", 0, "text node shared between templates");
		}
		this.textNodeEdits.put(n, text);
	}

	private void commit() {
		for (Map.Entry<ActionNode, List<String>> en : this.actionNodeEdits.entrySet()) {
			ensurePipelineContains(en.getKey().getPipeNode(), en.getValue());
		}
		for (Map.Entry<TextNode, String> en : this.textNodeEdits.entrySet()) {
			en.getKey().setText(en.getValue());
		}
	}

	// Ensures the pipeline ends with the escaper commands in s, merging a trailing
	// predefined escaper (html/urlquery) where equivalent.
	private static void ensurePipelineContains(PipeNode p, List<String> s) {
		if (s.isEmpty()) {
			return;
		}
		List<CommandNode> cmds = p.getCommands();
		List<String> want = new ArrayList<>(s);
		int pipelineLen = cmds.size();
		if (pipelineLen > 0) {
			pipelineLen = mergePredefinedEscaper(cmds, want, pipelineLen);
		}
		List<CommandNode> newCmds = new ArrayList<>(cmds.subList(0, pipelineLen));
		Set<String> inserted = new HashSet<>();
		for (CommandNode cmd : newCmds) {
			if (cmd.getFirstArgument() instanceof IdentifierNode idn) {
				inserted.add(normalizeEscFn(idn.getIdentifier()));
			}
		}
		for (String name : want) {
			if (!inserted.contains(normalizeEscFn(name))) {
				appendCmd(newCmds, newIdentCmd(name));
			}
		}
		cmds.clear();
		cmds.addAll(newCmds);
	}

	private static int mergePredefinedEscaper(List<CommandNode> cmds, List<String> want, int pipelineLen) {
		CommandNode lastCmd = cmds.get(pipelineLen - 1);
		if (!(lastCmd.getFirstArgument() instanceof IdentifierNode idNode)
				|| !PREDEFINED_ESCAPERS.contains(idNode.getIdentifier())) {
			return pipelineLen;
		}
		String esc = idNode.getIdentifier();
		if (cmds.size() == 1 && lastCmd.getArgumentCount() > 1) {
			// {{ esc arg1 .. argN }} -> {{ _eval_args_ arg1 .. argN | esc }}
			lastCmd.getArguments().set(0, new IdentifierNode("_eval_args_"));
			cmds.add(newIdentCmd(esc));
			pipelineLen++;
		}
		boolean dup = false;
		for (int i = 0; i < want.size(); i++) {
			if (escFnsEq(esc, want.get(i))) {
				want.set(i, esc);
				dup = true;
			}
		}
		return dup ? pipelineLen - 1 : pipelineLen;
	}

	private static void appendCmd(List<CommandNode> cmds, CommandNode cmd) {
		if (!cmds.isEmpty() && cmds.get(cmds.size() - 1).getFirstArgument() instanceof IdentifierNode last
				&& cmd.getFirstArgument() instanceof IdentifierNode next) {
			Set<String> redundant = REDUNDANT_FUNCS.get(last.getIdentifier());
			if (redundant != null && redundant.contains(next.getIdentifier())) {
				return;
			}
		}
		cmds.add(cmd);
	}

	private static CommandNode newIdentCmd(String identifier) {
		CommandNode cmd = new CommandNode();
		cmd.append(new IdentifierNode(identifier));
		return cmd;
	}

	private static boolean escFnsEq(String a, String b) {
		return normalizeEscFn(a).equals(normalizeEscFn(b));
	}

	private static String normalizeEscFn(String e) {
		return EQUIV_ESCAPERS.getOrDefault(e, e);
	}

	private static Context nudge(Context c) {
		switch (c.state) {
			case TAG -> {
				c.state = State.ATTR_NAME;
			}
			case BEFORE_VALUE -> {
				c.state = attrStartState(c.attr);
				c.delim = Delim.SPACE_OR_TAG_END;
				c.attr = Attr.NONE;
			}
			case AFTER_NAME -> {
				c.state = State.ATTR_NAME;
				c.attr = Attr.NONE;
			}
			default -> {
			}
		}
		return c;
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

	private static Context join(Context a, Context b, Node node, String nodeName) {
		if (a.state == State.ERROR) {
			return a;
		}
		if (b.state == State.ERROR) {
			return b;
		}
		if (a.state == State.DEAD) {
			return b;
		}
		if (b.state == State.DEAD) {
			return a;
		}
		if (a.eq(b)) {
			return a;
		}
		Context c = a.copy();
		c.urlPart = b.urlPart;
		if (c.eq(b)) {
			c.urlPart = UrlPart.UNKNOWN;
			return c;
		}
		c = a.copy();
		c.jsCtx = b.jsCtx;
		if (c.eq(b)) {
			c.jsCtx = JsCtx.UNKNOWN;
			return c;
		}
		Context na = nudge(a.copy());
		Context nb = nudge(b.copy());
		if (!(na.eq(a) && nb.eq(b))) {
			Context e = join(na, nb, node, nodeName);
			if (e.state != State.ERROR) {
				return e;
			}
		}
		return errCtx(EscapeErrorCode.ERR_BRANCH_END, node, 0,
				"{{" + nodeName + "}} branches end in different contexts: " + a + ", " + b);
	}

	private static Context dead() {
		Context c = new Context();
		c.state = State.DEAD;
		return c;
	}

	private static Context errCtx(EscapeErrorCode code, Node node, int line, String description) {
		Context c = new Context();
		c.state = State.ERROR;
		c.err = new EscapeError(code, node, "", line, description);
		return c;
	}

	private static String delimEnds(Delim delim) {
		return switch (delim) {
			case DOUBLE_QUOTE -> "\"";
			case SINGLE_QUOTE -> "'";
			case SPACE_OR_TAG_END -> " \t\n\f\r>";
			default -> "";
		};
	}

	private static boolean containsNewline(byte[] s, int from, int to) {
		for (int i = from; i < to; i++) {
			int x = s[i] & 0xff;
			if (x == '\n' || x == '\r') {
				return true;
			}
			if (x == 0xe2 && i + 2 < to && (s[i + 1] & 0xff) == 0x80
					&& ((s[i + 2] & 0xff) == 0xa8 || (s[i + 2] & 0xff) == 0xa9)) {
				return true;
			}
		}
		return false;
	}

	private record Tree(Context context, String name) {
	}

	private record Body(Context context, boolean ok) {
	}

	private static final class RangeContext {

		private final RangeContext outer;

		private final List<Context> breaks = new ArrayList<>();

		private final List<Context> continues = new ArrayList<>();

		RangeContext(RangeContext outer) {
			this.outer = outer;
		}

	}

}
