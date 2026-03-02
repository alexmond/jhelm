package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Sprig Dict (map) manipulation functions. Based on:
 * <a href="https://masterminds.github.io/sprig/dicts.html">Sprig Dicts</a>
 */
public final class DictFunctions {

	private DictFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();
		functions.put("dict", dict());
		functions.put("get", get());
		functions.put("set", set());
		functions.put("unset", unset());
		functions.put("hasKey", hasKey());
		functions.put("mustHasKey", mustHasKey());
		functions.put("pluck", pluck());
		functions.put("dig", dig());
		functions.put("merge", merge());
		functions.put("mergeOverwrite", mergeOverwrite());
		functions.put("mustMerge", mustMerge());
		functions.put("mustMergeOverwrite", mustMergeOverwrite());
		functions.put("keys", keys());
		functions.put("mustKeys", mustKeys());
		functions.put("pick", pick());
		functions.put("mustPick", mustPick());
		functions.put("omit", omit());
		functions.put("mustOmit", mustOmit());
		functions.put("values", values());
		functions.put("mustValues", mustValues());
		functions.put("deepCopy", deepCopy());
		functions.put("mustDeepCopy", mustDeepCopy());
		return functions;
	}

	private static Function dict() {
		return (args) -> {
			Map<String, Object> map = new LinkedHashMap<>();
			for (int i = 0; i < args.length; i += 2) {
				if (i + 1 < args.length) {
					map.put(String.valueOf(args[i]), args[i + 1]);
				}
			}
			return map;
		};
	}

	private static Function get() {
		return (args) -> {
			if (args.length < 2 || !(args[0] instanceof Map)) {
				return null;
			}
			return ((Map<?, ?>) args[0]).get(String.valueOf(args[1]));
		};
	}

	private static Function set() {
		return (args) -> {
			if (args.length < 3) {
				return (args.length > 0) ? args[0] : null;
			}
			Object dict = args[0];
			String key = String.valueOf(args[1]);
			Object value = args[2];
			if (dict instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) dict;
				map.put(key, value);
				return map;
			}
			Map<String, Object> newMap = new HashMap<>();
			newMap.put(key, value);
			return newMap;
		};
	}

	private static Function unset() {
		return (args) -> {
			if (args.length < 2) {
				return (args.length > 0) ? args[0] : null;
			}
			Object dict = args[0];
			String key = String.valueOf(args[1]);
			if (dict instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) dict;
				map.remove(key);
				return map;
			}
			return dict;
		};
	}

	private static Function hasKey() {
		return (args) -> {
			if (args.length < 2 || !(args[0] instanceof Map)) {
				return false;
			}
			return ((Map<?, ?>) args[0]).containsKey(String.valueOf(args[1]));
		};
	}

	private static Function mustHasKey() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustHasKey: insufficient arguments");
			}
			boolean result = (boolean) hasKey().invoke(args);
			if (!result) {
				throw new RuntimeException("mustHasKey: key not found");
			}
			return result;
		};
	}

	private static Function pluck() {
		return (args) -> {
			if (args.length < 2) {
				return Collections.emptyList();
			}
			String key = String.valueOf(args[0]);
			java.util.List<Object> result = new ArrayList<>();
			for (int i = 1; i < args.length; i++) {
				if (args[i] instanceof Map) {
					Object val = ((Map<?, ?>) args[i]).get(key);
					if (val != null) {
						result.add(val);
					}
				}
			}
			return result;
		};
	}

	private static Function dig() {
		return (args) -> {
			if (args.length < 2) {
				return null;
			}
			Object defaultValue = (args.length > 2) ? args[args.length - 2] : null;
			Object current = args[args.length - 1];
			for (int i = 0; i < args.length - 2; i++) {
				if (!(current instanceof Map)) {
					return defaultValue;
				}
				String key = String.valueOf(args[i]);
				Map<?, ?> map = (Map<?, ?>) current;
				if (!map.containsKey(key)) {
					return defaultValue;
				}
				current = map.get(key);
			}
			return (current != null) ? current : defaultValue;
		};
	}

	@SuppressWarnings("unchecked")
	private static Function merge() {
		return (args) -> {
			if (args.length == 0) {
				return new LinkedHashMap<>();
			}
			Map<String, Object> dst = (args[0] instanceof Map) ? (Map<String, Object>) args[0] : new LinkedHashMap<>();
			for (int i = 1; i < args.length; i++) {
				if (args[i] instanceof Map) {
					deepMerge(dst, (Map<String, Object>) args[i], false);
				}
			}
			return dst;
		};
	}

	@SuppressWarnings("unchecked")
	private static Function mergeOverwrite() {
		return (args) -> {
			if (args.length == 0) {
				return new LinkedHashMap<>();
			}
			Map<String, Object> dst = (args[0] instanceof Map) ? (Map<String, Object>) args[0] : new LinkedHashMap<>();
			for (int i = 1; i < args.length; i++) {
				if (args[i] instanceof Map) {
					deepMerge(dst, (Map<String, Object>) args[i], true);
				}
			}
			return dst;
		};
	}

	@SuppressWarnings("unchecked")
	private static void deepMerge(Map<String, Object> dst, Map<String, Object> src, boolean overwrite) {
		for (Map.Entry<String, Object> entry : src.entrySet()) {
			String key = entry.getKey();
			Object srcVal = entry.getValue();
			if (dst.containsKey(key)) {
				Object dstVal = dst.get(key);
				if (dstVal instanceof Map && srcVal instanceof Map) {
					deepMerge((Map<String, Object>) dstVal, (Map<String, Object>) srcVal, overwrite);
				}
				else if (overwrite || dstVal == null) {
					dst.put(key, srcVal);
				}
			}
			else {
				dst.put(key, srcVal);
			}
		}
	}

	private static Function mustMerge() {
		return (args) -> {
			if (args.length == 0) {
				throw new RuntimeException("mustMerge: no arguments provided");
			}
			return merge().invoke(args);
		};
	}

	private static Function mustMergeOverwrite() {
		return (args) -> {
			if (args.length == 0) {
				throw new RuntimeException("mustMergeOverwrite: no arguments provided");
			}
			return mergeOverwrite().invoke(args);
		};
	}

	private static Function keys() {
		return (args) -> (args.length > 0 && args[0] instanceof Map) ? new ArrayList<>(((Map<?, ?>) args[0]).keySet())
				: Collections.emptyList();
	}

	private static Function mustKeys() {
		return (args) -> {
			if (args.length == 0 || !(args[0] instanceof Map)) {
				throw new RuntimeException("mustKeys: argument is not a map");
			}
			return new ArrayList<>(((Map<?, ?>) args[0]).keySet());
		};
	}

	private static Function pick() {
		return (args) -> {
			if (args.length < 2 || !(args[0] instanceof Map)) {
				return (args.length > 0) ? args[0] : Map.of();
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> src = (Map<String, Object>) args[0];
			Map<String, Object> dest = new LinkedHashMap<>();
			for (int i = 1; i < args.length; i++) {
				String key = String.valueOf(args[i]);
				if (src.containsKey(key)) {
					dest.put(key, src.get(key));
				}
			}
			return dest;
		};
	}

	private static Function mustPick() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustPick: insufficient arguments");
			}
			return pick().invoke(args);
		};
	}

	private static Function omit() {
		return (args) -> {
			if (args.length < 2 || !(args[0] instanceof Map)) {
				return (args.length > 0) ? args[0] : Map.of();
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) args[0]);
			for (int i = 1; i < args.length; i++) {
				map.remove(String.valueOf(args[i]));
			}
			return map;
		};
	}

	private static Function mustOmit() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustOmit: insufficient arguments");
			}
			return omit().invoke(args);
		};
	}

	private static Function values() {
		return (args) -> (args.length > 0 && args[0] instanceof Map) ? new ArrayList<>(((Map<?, ?>) args[0]).values())
				: Collections.emptyList();
	}

	private static Function mustValues() {
		return (args) -> {
			if (args.length == 0 || !(args[0] instanceof Map)) {
				throw new RuntimeException("mustValues: argument is not a map");
			}
			return new ArrayList<>(((Map<?, ?>) args[0]).values());
		};
	}

	@SuppressWarnings("unchecked")
	private static Function deepCopy() {
		return (args) -> {
			if (args.length == 0) {
				return null;
			}
			Object obj = args[0];
			if (obj instanceof Map) {
				Map<String, Object> copy = new LinkedHashMap<>();
				for (Map.Entry<String, Object> entry : ((Map<String, Object>) obj).entrySet()) {
					copy.put(entry.getKey(), entry.getValue()); // Shallow copy for now
				}
				return copy;
			}
			else if (obj instanceof Collection) {
				return new ArrayList<>((Collection<?>) obj);
			}
			return obj;
		};
	}

	private static Function mustDeepCopy() {
		return (args) -> {
			if (args.length == 0) {
				throw new RuntimeException("mustDeepCopy: no arguments provided");
			}
			return deepCopy().invoke(args);
		};
	}

}
