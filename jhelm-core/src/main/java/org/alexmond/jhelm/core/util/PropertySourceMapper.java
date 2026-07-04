package org.alexmond.jhelm.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.model.Environment;

/**
 * Turns a Spring Cloud Config Server {@link Environment} into a nested values map.
 * <p>
 * Each {@code propertySource} carries a flat map of dotted / {@code [i]}-indexed keys.
 * For every source this mapper:
 * <ol>
 * <li><b>strips</b> activation directives ({@code spring.config.activate.*} and
 * {@code jhelm.config.activate.*}) so they never reach {@code .Values} — the server does
 * not remove them itself;</li>
 * <li><b>un-flattens</b> the remaining dotted / indexed keys into nested maps and
 * lists.</li>
 * </ol>
 * Sources are then merged <b>first-wins</b> (the config server lists them highest
 * precedence first), matching the server's own ordering.
 */
public final class PropertySourceMapper {

	private static final String SPRING_ACTIVATE_PREFIX = "spring.config.activate.";

	private static final String JHELM_ACTIVATE_PREFIX = "jhelm.config.activate.";

	private PropertySourceMapper() {
	}

	/**
	 * Reduce an Environment to a single nested values map (strip + un-flatten +
	 * first-wins merge of its property sources).
	 * @param environment the parsed config-server response (may be {@code null})
	 * @return the merged nested values ({@code {}} if there are no sources)
	 */
	public static Map<String, Object> toValues(Environment environment) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (environment == null || environment.getPropertySources() == null) {
			return result;
		}
		List<Environment.PropertySource> sources = environment.getPropertySources();
		// propertySources are highest-first; merge from lowest to highest so the highest
		// (index 0) is applied last and wins.
		for (int i = sources.size() - 1; i >= 0; i--) {
			Environment.PropertySource source = sources.get(i);
			if (source == null || source.getSource() == null) {
				continue;
			}
			ValuesLoader.deepMerge(result, unflatten(source.getSource()));
		}
		return result;
	}

	/**
	 * Un-flatten a single flat (dotted / indexed) source map into nested maps and lists,
	 * dropping activation-directive keys.
	 * @param flat the flat source map
	 * @return the nested map
	 */
	static Map<String, Object> unflatten(Map<String, Object> flat) {
		Map<String, Object> root = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : flat.entrySet()) {
			String key = entry.getKey();
			if (isActivationKey(key)) {
				continue;
			}
			setPath(root, parseKey(key), 0, entry.getValue());
		}
		return root;
	}

	private static boolean isActivationKey(String key) {
		return key.startsWith(SPRING_ACTIVATE_PREFIX) || key.startsWith(JHELM_ACTIVATE_PREFIX);
	}

	/**
	 * Parse a flat key such as {@code a.b[0].c} into an ordered token list (map keys and
	 * list indices).
	 */
	private static List<Token> parseKey(String key) {
		List<Token> tokens = new ArrayList<>();
		for (String part : key.split("\\.", -1)) {
			int bracket = part.indexOf('[');
			String name = (bracket < 0) ? part : part.substring(0, bracket);
			if (!name.isEmpty()) {
				tokens.add(Token.key(name));
			}
			if (bracket >= 0) {
				// one or more [i] index groups, e.g. x[0][1]
				String rest = part.substring(bracket);
				for (String idx : rest.split("]")) {
					String digits = idx.replace("[", "").trim();
					if (!digits.isEmpty()) {
						tokens.add(Token.index(Integer.parseInt(digits)));
					}
				}
			}
		}
		return tokens;
	}

	@SuppressWarnings("unchecked")
	private static void setPath(Object node, List<Token> tokens, int idx, Object value) {
		Token token = tokens.get(idx);
		boolean last = idx == tokens.size() - 1;
		if (token.index < 0) {
			Map<String, Object> map = (Map<String, Object>) node;
			if (last) {
				map.put(token.name, value);
				return;
			}
			Object child = getOrCreate(map.get(token.name), tokens.get(idx + 1));
			map.put(token.name, child);
			setPath(child, tokens, idx + 1, value);
		}
		else {
			List<Object> list = (List<Object>) node;
			pad(list, token.index);
			if (last) {
				list.set(token.index, value);
				return;
			}
			Object child = getOrCreate(list.get(token.index), tokens.get(idx + 1));
			list.set(token.index, child);
			setPath(child, tokens, idx + 1, value);
		}
	}

	private static Object getOrCreate(Object existing, Token next) {
		if (next.index < 0) {
			return (existing instanceof Map) ? existing : new LinkedHashMap<>();
		}
		return (existing instanceof List) ? existing : new ArrayList<>();
	}

	private static void pad(List<Object> list, int index) {
		while (list.size() <= index) {
			list.add(null);
		}
	}

	/**
	 * A single segment of a flat key: either a map key ({@code index < 0}) or a list
	 * index.
	 */
	private static final class Token {

		private final String name;

		private final int index;

		private Token(String name, int index) {
			this.name = name;
			this.index = index;
		}

		static Token key(String name) {
			return new Token(name, -1);
		}

		static Token index(int index) {
			return new Token(null, index);
		}

	}

}
