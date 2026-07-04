package org.alexmond.jhelm.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The set of active value profiles, plus the Spring-Boot-style profile-expression matcher
 * used to gate {@code spring.config.activate.on-profile} documents (see
 * {@link ValuesLoader}).
 * <p>
 * Active profiles are ordered — later profiles win when profile sidecar files
 * ({@code values-<profile>.yaml}) are applied, matching Spring's "last one wins" rule.
 * The expression grammar mirrors Spring's {@code Profiles.of(...)}: profile names
 * combined with {@code !} (not), {@code &} (and), {@code |} (or), parentheses for
 * grouping, and comma-separated lists treated as OR ("at least one must match"). This
 * matches the grammar a Spring Cloud Config Server honours server-side, so the same
 * directive resolves identically for local files and config-server-sourced values.
 */
public final class ValuesProfiles {

	/**
	 * Document-activation directive key (flat, dotted form). A values document carrying
	 * this key is applied only when its profile expression matches the active profiles;
	 * the key itself is stripped before values reach {@code .Values}.
	 */
	public static final String ON_PROFILE_KEY = "spring.config.activate.on-profile";

	/**
	 * Cloud-platform activation directive key. jhelm always targets Kubernetes and has no
	 * client-side platform signal, so it does not gate on this key — but it is stripped
	 * so it never leaks into {@code .Values}.
	 */
	public static final String ON_CLOUD_PLATFORM_KEY = "spring.config.activate.on-cloud-platform";

	private static final ValuesProfiles NONE = new ValuesProfiles(List.of());

	private final List<String> active;

	private final Set<String> activeSet;

	private ValuesProfiles(List<String> active) {
		this.active = List.copyOf(active);
		this.activeSet = Set.copyOf(active);
	}

	/**
	 * Returns the shared empty profile set (no profiles active).
	 * @return an empty {@link ValuesProfiles}
	 */
	public static ValuesProfiles none() {
		return NONE;
	}

	/**
	 * Builds a profile set from raw activation strings. Each entry may itself be a
	 * comma-separated list (e.g. {@code "prod,eu"}); entries are split on commas,
	 * trimmed, blanks dropped, and duplicates removed while preserving first-seen order.
	 * @param raw activation strings (from {@code --profile}, an env var or a property);
	 * may be {@code null}
	 * @return the resolved profile set (never {@code null})
	 */
	public static ValuesProfiles of(Collection<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return NONE;
		}
		Set<String> ordered = new LinkedHashSet<>();
		for (String entry : raw) {
			if (entry == null) {
				continue;
			}
			for (String part : entry.split(",")) {
				String trimmed = part.trim();
				if (!trimmed.isEmpty()) {
					ordered.add(trimmed);
				}
			}
		}
		return ordered.isEmpty() ? NONE : new ValuesProfiles(new ArrayList<>(ordered));
	}

	/**
	 * Returns the active profiles in activation order (later profiles win on merge).
	 * @return an unmodifiable, ordered list of active profile names
	 */
	public List<String> active() {
		return Collections.unmodifiableList(active);
	}

	/**
	 * Returns {@code true} when no profiles are active.
	 * @return {@code true} if the set is empty
	 */
	public boolean isEmpty() {
		return active.isEmpty();
	}

	/**
	 * Evaluates a Spring-style profile expression against the active profiles.
	 * @param expression a profile expression (single name, or names combined with
	 * {@code !}, {@code &}, {@code |}, parentheses and comma-lists); {@code null} or
	 * blank means "no constraint" and matches
	 * @return {@code true} if the expression matches the active profiles
	 * @throws IllegalArgumentException if the expression is syntactically invalid
	 */
	public boolean matches(String expression) {
		if (expression == null) {
			return true;
		}
		String expr = expression.trim();
		if (expr.isEmpty()) {
			return true;
		}
		return new ExpressionParser(expr, activeSet).evaluate();
	}

	/**
	 * Recursive-descent evaluator for profile expressions. Precedence (lowest to
	 * highest): {@code |} / {@code ,} (or), {@code &} (and), {@code !} (not), then
	 * parenthesised groups and bare profile names.
	 */
	private static final class ExpressionParser {

		private final String source;

		private final Set<String> active;

		private int pos;

		ExpressionParser(String source, Set<String> active) {
			this.source = source;
			this.active = active;
		}

		boolean evaluate() {
			boolean result = parseOr();
			skipWhitespace();
			if (pos < source.length()) {
				throw new IllegalArgumentException("Invalid profile expression: " + source);
			}
			return result;
		}

		private boolean parseOr() {
			boolean value = parseAnd();
			while (true) {
				skipWhitespace();
				char c = peek();
				if (c == '|' || c == ',') {
					pos++;
					boolean rhs = parseAnd();
					value = value || rhs;
				}
				else {
					return value;
				}
			}
		}

		private boolean parseAnd() {
			boolean value = parseNot();
			while (true) {
				skipWhitespace();
				if (peek() == '&') {
					pos++;
					boolean rhs = parseNot();
					value = value && rhs;
				}
				else {
					return value;
				}
			}
		}

		private boolean parseNot() {
			skipWhitespace();
			if (peek() == '!') {
				pos++;
				return !parseNot();
			}
			return parseAtom();
		}

		private boolean parseAtom() {
			skipWhitespace();
			if (peek() == '(') {
				pos++;
				boolean value = parseOr();
				skipWhitespace();
				if (peek() != ')') {
					throw new IllegalArgumentException("Unbalanced parentheses in profile expression: " + source);
				}
				pos++;
				return value;
			}
			String name = parseName();
			return active.contains(name);
		}

		private String parseName() {
			int start = pos;
			while (pos < source.length() && isNameChar(source.charAt(pos))) {
				pos++;
			}
			if (pos == start) {
				throw new IllegalArgumentException("Expected a profile name in expression: " + source);
			}
			return source.substring(start, pos);
		}

		private static boolean isNameChar(char c) {
			return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == '+' || c == '@';
		}

		private char peek() {
			return (pos < source.length()) ? source.charAt(pos) : '\0';
		}

		private void skipWhitespace() {
			while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
				pos++;
			}
		}

	}

}
