package org.alexmond.jhelm.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.alexmond.jhelm.core.exception.JhelmException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Utility for building value override maps from {@code -f}/ {@code --values} files and
 * the command-line {@code --set} family of flags.
 * <p>
 * Files are loaded in order using {@link ValuesLoader} (which supports multi-document
 * YAML). The {@code --set} flags are parsed afterwards so they take precedence over file
 * values. Dot-separated keys create nested maps (e.g. {@code outer.inner=v} produces
 * {@code {outer: {inner: "v"}}}).
 * <p>
 * The four {@code --set} variants differ only in how the value is interpreted:
 * <ul>
 * <li>{@code --set} — the value is coerced to a typed scalar (boolean, integer,
 * {@code null} or string), matching Helm's behaviour.</li>
 * <li>{@code --set-string} — the value is always the raw string (no coercion).</li>
 * <li>{@code --set-file} — the value is the contents of the file at the given path.</li>
 * <li>{@code --set-json} — the value is parsed as JSON into a map, list or scalar.</li>
 * </ul>
 */
public final class ValuesOverrides {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private ValuesOverrides() {
	}

	/**
	 * Returns the given values map, or an empty map if {@code null}. Convenience method
	 * to avoid repeated null-coalescing in controllers.
	 * @param values the values map (may be {@code null})
	 * @return the values map, or {@code Map.of()} if {@code null}
	 */
	public static Map<String, Object> safeValues(Map<String, Object> values) {
		return (values != null) ? values : Map.of();
	}

	/**
	 * Build a merged override map from values files and {@code --set} arguments.
	 * @param files paths to YAML values files; {@code null} or empty means none
	 * @param setArgs {@code key=value} strings; {@code null} or empty means none
	 * @return merged override map
	 * @throws IOException if any values file cannot be read
	 */
	public static Map<String, Object> parse(List<String> files, List<String> setArgs) throws IOException {
		return parse(files, setArgs, null, null, null);
	}

	/**
	 * Build a merged override map from values files and the full {@code --set} family of
	 * arguments.
	 * <p>
	 * Groups are applied in increasing order of precedence, with each later group
	 * overriding earlier ones: files &rarr; {@code --set} &rarr; {@code --set-string}
	 * &rarr; {@code --set-file} &rarr; {@code --set-json}.
	 * @param files paths to YAML values files; {@code null} or empty means none
	 * @param setArgs {@code key=value} strings, coerced to typed scalars; {@code null} or
	 * empty means none
	 * @param setStringArgs {@code key=value} strings kept as raw strings; {@code null} or
	 * empty means none
	 * @param setFileArgs {@code key=path} strings whose value is the file contents;
	 * {@code null} or empty means none
	 * @param setJsonArgs {@code key=json} strings whose value is parsed as JSON;
	 * {@code null} or empty means none
	 * @return merged override map
	 * @throws IOException if any values file cannot be read
	 */
	public static Map<String, Object> parse(List<String> files, List<String> setArgs, List<String> setStringArgs,
			List<String> setFileArgs, List<String> setJsonArgs) throws IOException {
		return parse(files, ValuesProfiles.none(), setArgs, setStringArgs, setFileArgs, setJsonArgs);
	}

	/**
	 * Build a merged override map with value profiles applied to each {@code -f} file
	 * (multi-document {@code spring.config.activate.on-profile} gating and
	 * {@code <name>-<profile>.<ext>} sidecars). Precedence is unchanged: files &rarr;
	 * {@code --set} &rarr; {@code --set-string} &rarr; {@code --set-file} &rarr;
	 * {@code --set-json}.
	 * @param files paths to YAML values files; {@code null} or empty means none
	 * @param profiles the active value profiles
	 * @param setArgs {@code key=value} strings, coerced to typed scalars
	 * @param setStringArgs {@code key=value} strings kept as raw strings
	 * @param setFileArgs {@code key=path} strings whose value is the file contents
	 * @param setJsonArgs {@code key=json} strings whose value is parsed as JSON
	 * @return merged override map
	 * @throws IOException if any values file cannot be read
	 */
	public static Map<String, Object> parse(List<String> files, ValuesProfiles profiles, List<String> setArgs,
			List<String> setStringArgs, List<String> setFileArgs, List<String> setJsonArgs) throws IOException {
		Map<String, Object> merged = new HashMap<>();
		if (files != null) {
			for (String path : files) {
				Map<String, Object> fileValues;
				if (ValuesLoader.isUrl(path)) {
					fileValues = ValuesLoader.loadFromUrl(path, profiles);
				}
				else {
					fileValues = ValuesLoader.load(new File(path), profiles);
				}
				ValuesLoader.deepMerge(merged, fileValues);
			}
		}
		if (setArgs != null) {
			for (String arg : setArgs) {
				applySet(merged, arg);
			}
		}
		if (setStringArgs != null) {
			for (String arg : setStringArgs) {
				applySetString(merged, arg);
			}
		}
		if (setFileArgs != null) {
			for (String arg : setFileArgs) {
				applySetFile(merged, arg);
			}
		}
		if (setJsonArgs != null) {
			for (String arg : setJsonArgs) {
				applySetJson(merged, arg);
			}
		}
		return merged;
	}

	/**
	 * Parse a single {@code key=value} set argument and apply it to {@code target},
	 * coercing the value to a typed scalar like Helm: {@code null} &rarr; {@code null},
	 * {@code true}/{@code false} &rarr; {@link Boolean}, a canonical integer &rarr;
	 * {@link Long} (floats and leading-zero numbers stay strings), an empty value &rarr;
	 * the empty string, and everything else stays a {@link String}.
	 * <p>
	 * Keys may use dot notation to set nested values (e.g. {@code a.b=v}).
	 * @param target the map to merge into
	 * @param arg the {@code key=value} string
	 */
	static void applySet(Map<String, Object> target, String arg) {
		applyTyped(target, arg, ValuesOverrides::coerce);
	}

	/**
	 * Parse a single {@code key=value} set argument and apply it to {@code target},
	 * keeping the value as the raw string with no type coercion.
	 * @param target the map to merge into
	 * @param arg the {@code key=value} string
	 */
	static void applySetString(Map<String, Object> target, String arg) {
		applyTyped(target, arg, Function.identity());
	}

	/**
	 * Parse a single {@code key=path} set argument and apply it to {@code target}, with
	 * the value being the contents of the file at {@code path} read as a string.
	 * @param target the map to merge into
	 * @param arg the {@code key=path} string
	 * @throws JhelmException if the file cannot be read
	 */
	static void applySetFile(Map<String, Object> target, String arg) {
		applyTyped(target, arg, ValuesOverrides::readFile);
	}

	/**
	 * Parse a single {@code key=json} set argument and apply it to {@code target}, with
	 * the value being the JSON parsed into a map, list or scalar.
	 * @param target the map to merge into
	 * @param arg the {@code key=json} string
	 * @throws JhelmException if the JSON cannot be parsed
	 */
	static void applySetJson(Map<String, Object> target, String arg) {
		applyTyped(target, arg, ValuesOverrides::parseJson);
	}

	private static void applyTyped(Map<String, Object> target, String arg, Function<String, ?> valueFn) {
		int eq = arg.indexOf('=');
		if (eq < 0) {
			return;
		}
		String keyPath = arg.substring(0, eq);
		String raw = arg.substring(eq + 1);
		setAtPath(target, keyPath, valueFn.apply(raw));
	}

	@SuppressWarnings("unchecked")
	private static void setAtPath(Map<String, Object> target, String keyPath, Object value) {
		String[] parts = keyPath.split("\\.", -1);
		Map<String, Object> current = target;
		for (int i = 0; i < parts.length - 1; i++) {
			Object existing = current.get(parts[i]);
			if (existing instanceof Map) {
				current = (Map<String, Object>) existing;
			}
			else {
				Map<String, Object> nested = new HashMap<>();
				current.put(parts[i], nested);
				current = nested;
			}
		}
		current.put(parts[parts.length - 1], value);
	}

	private static Object coerce(String value) {
		if (value.isEmpty()) {
			return "";
		}
		if ("null".equals(value)) {
			return null;
		}
		if ("true".equals(value)) {
			return Boolean.TRUE;
		}
		if ("false".equals(value)) {
			return Boolean.FALSE;
		}
		try {
			long parsed = Long.parseLong(value);
			// Only coerce when the value round-trips exactly to its canonical form, so
			// leading-zero strings ("007"), explicit signs ("+5") and floats stay strings
			// — matching Helm's strvals (verified: helm --set keeps 007 and 1.5 as
			// strings,
			// coerces 3 to int64).
			if (Long.toString(parsed).equals(value)) {
				return parsed;
			}
		}
		catch (NumberFormatException ignored) {
			// not an integer literal; fall through to string
		}
		return value;
	}

	private static Object readFile(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException ex) {
			throw new JhelmException("Failed to read --set-file value from '" + path + "': " + ex.getMessage(), ex);
		}
	}

	private static Object parseJson(String json) {
		try {
			return JSON_MAPPER.readValue(json, Object.class);
		}
		catch (RuntimeException ex) {
			throw new JhelmException("Failed to parse --set-json value '" + json + "': " + ex.getMessage(), ex);
		}
	}

}
