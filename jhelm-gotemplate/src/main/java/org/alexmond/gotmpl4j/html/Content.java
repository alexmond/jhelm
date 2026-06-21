package org.alexmond.gotmpl4j.html;

import java.util.ArrayList;
import java.util.List;

import org.alexmond.gotmpl4j.GoFmt;

/**
 * Resolves a template value (or values) to a string and its {@link ContentType}, the way
 * Go {@code html/template}'s {@code stringify} does. The contextual escapers call this
 * first so a {@link SafeContent} of the matching type can be passed through verbatim
 * while everything else is treated as {@link ContentType#PLAIN} untrusted text.
 */
public final class Content {

	private Content() {
	}

	/**
	 * Converts arguments to a string and the content type of the result, mirroring Go
	 * {@code html/template}'s {@code stringify}. A single {@link SafeContent} argument
	 * keeps its type; a single {@link String} is {@link ContentType#PLAIN}; anything else
	 * (or multiple arguments) is rendered like {@code fmt.Sprint} as
	 * {@link ContentType#PLAIN}, skipping {@code null} arguments for backward
	 * compatibility (Go issue 25875).
	 * @param args the arguments (typically the single evaluated pipeline value)
	 * @return the rendered text and its content type
	 */
	public static Stringified stringify(Object... args) {
		if (args.length == 1) {
			Object a = indirect(args[0]);
			if (a instanceof SafeContent safe) {
				return new Stringified(safe.value(), safe.contentType());
			}
			if (a instanceof String s) {
				return new Stringified(s, ContentType.PLAIN);
			}
		}
		List<Object> kept = new ArrayList<>(args.length);
		for (Object arg : args) {
			// Skip untyped nil arguments for backward compatibility; without this they
			// would be output as <nil>, escaped (Go issue 25875).
			if (arg == null) {
				continue;
			}
			kept.add(indirect(arg));
		}
		return new Stringified(sprintAll(kept), ContentType.PLAIN);
	}

	// Java has no pointer indirection (Go dereferences pointers / unwraps to a
	// fmt.Stringer or error here); the value is returned as-is.
	private static Object indirect(Object a) {
		return a;
	}

	// Mirrors fmt.Sprint: operands are rendered with %v and a space is inserted between
	// two adjacent operands when neither is a string.
	private static String sprintAll(List<Object> operands) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < operands.size(); i++) {
			if (i > 0 && !isStringLike(operands.get(i - 1)) && !isStringLike(operands.get(i))) {
				sb.append(' ');
			}
			sb.append(GoFmt.sprint(operands.get(i)));
		}
		return sb.toString();
	}

	private static boolean isStringLike(Object o) {
		return o instanceof String || o instanceof SafeContent;
	}

	/**
	 * The result of {@link #stringify(Object...)}: the rendered text and the content type
	 * it should be treated as.
	 *
	 * @param text the rendered string
	 * @param type the content type
	 */
	public record Stringified(String text, ContentType type) {
	}

}
