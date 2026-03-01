package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Sprig logic and control flow functions Based on: <a href=
 * "https://masterminds.github.io/sprig/defaults.html">https://masterminds.github.io/sprig/defaults.html</a>
 */
public final class LogicFunctions {

	private LogicFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		functions.put("default", defaultFunc());
		functions.put("empty", empty());
		functions.put("coalesce", coalesce());
		functions.put("ternary", ternary());
		functions.put("fail", fail());
		functions.put("all", all());
		functions.put("any", any());

		return functions;
	}

	private static Function defaultFunc() {
		return (args) -> {
			if (args.length < 2) {
				return (args.length == 1) ? args[0] : null;
			}
			Object defaultVal = args[0];
			Object actualVal = args[1];
			return (isTruthy(actualVal)) ? actualVal : defaultVal;
		};
	}

	private static Function empty() {
		return (args) -> args.length == 0 || !isTruthy(args[0]);
	}

	private static Function coalesce() {
		return (args) -> {
			for (Object arg : args) {
				if (isTruthy(arg)) {
					return arg;
				}
			}
			return null;
		};
	}

	private static Function ternary() {
		return (args) -> {
			if (args.length < 3) {
				return "";
			}
			return (isTruthy(args[2])) ? args[0] : args[1];
		};
	}

	private static Function fail() {
		return (args) -> {
			throw new RuntimeException((args.length > 0) ? String.valueOf(args[0]) : "fail");
		};
	}

	private static Function all() {
		return (args) -> {
			for (Object arg : args) {
				if (!isTruthy(arg)) {
					return false;
				}
			}
			return true;
		};
	}

	private static Function any() {
		return (args) -> {
			for (Object arg : args) {
				if (isTruthy(arg)) {
					return true;
				}
			}
			return false;
		};
	}

	private static boolean isTruthy(Object value) {
		return switch (value) {
			case null -> false;
			case Boolean b -> b;
			case String s -> !s.isEmpty();
			case Number number -> number.doubleValue() != 0;
			case Collection<?> collection -> !collection.isEmpty();
			case Map<?, ?> map -> !map.isEmpty();
			default -> true;
		};
	}

}
