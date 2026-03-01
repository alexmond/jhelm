package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Path manipulation functions from Sprig library. Implements Go's {@code path} package
 * functions for POSIX-style paths.
 *
 * @see <a href="https://masterminds.github.io/sprig/paths.html">Sprig Path Functions</a>
 */
public final class PathFunctions {

	private PathFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();
		functions.put("base", base());
		functions.put("dir", dir());
		functions.put("ext", ext());
		functions.put("clean", clean());
		functions.put("isAbs", isAbs());
		// OS-specific path aliases (in Helm charts, these behave the same as POSIX)
		functions.put("osBase", base());
		functions.put("osDir", dir());
		functions.put("osExt", ext());
		functions.put("osClean", clean());
		functions.put("osIsAbs", isAbs());
		return functions;
	}

	/**
	 * Returns the last element of a path. Equivalent to Go's {@code path.Base}.
	 */
	private static Function base() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return ".";
			}
			String path = String.valueOf(args[0]);
			if (path.isEmpty()) {
				return ".";
			}
			if ("/".equals(path)) {
				return "/";
			}
			// Remove trailing slashes
			while (path.length() > 1 && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			int lastSlash = path.lastIndexOf('/');
			if (lastSlash >= 0) {
				return path.substring(lastSlash + 1);
			}
			return path;
		};
	}

	/**
	 * Returns all but the last element of a path. Equivalent to Go's {@code path.Dir}.
	 */
	private static Function dir() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return ".";
			}
			String path = String.valueOf(args[0]);
			if (path.isEmpty()) {
				return ".";
			}
			int lastSlash = path.lastIndexOf('/');
			if (lastSlash < 0) {
				return ".";
			}
			String dir = path.substring(0, lastSlash);
			if (dir.isEmpty()) {
				return "/";
			}
			return dir;
		};
	}

	/**
	 * Returns the file extension. Equivalent to Go's {@code path.Ext}.
	 */
	private static Function ext() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}
			String path = String.valueOf(args[0]);
			int lastDot = path.lastIndexOf('.');
			int lastSlash = path.lastIndexOf('/');
			if (lastDot > lastSlash) {
				return path.substring(lastDot);
			}
			return "";
		};
	}

	/**
	 * Returns the shortest path name equivalent to path by purely lexical processing.
	 * Equivalent to Go's {@code path.Clean}.
	 */
	private static Function clean() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return ".";
			}
			String path = String.valueOf(args[0]);
			if (path.isEmpty()) {
				return ".";
			}
			// Normalize multiple slashes and resolve . and ..
			boolean rooted = path.startsWith("/");
			String[] parts = path.split("/");
			java.util.List<String> result = new java.util.ArrayList<>();
			for (String part : parts) {
				if (part.isEmpty() || ".".equals(part)) {
					continue;
				}
				if ("..".equals(part)) {
					if (!result.isEmpty() && !"..".equals(result.get(result.size() - 1))) {
						result.remove(result.size() - 1);
					}
					else if (!rooted) {
						result.add(part);
					}
				}
				else {
					result.add(part);
				}
			}
			String cleaned = String.join("/", result);
			if (rooted) {
				cleaned = "/" + cleaned;
			}
			if (cleaned.isEmpty()) {
				return rooted ? "/" : ".";
			}
			return cleaned;
		};
	}

	/**
	 * Reports whether the path is absolute. Equivalent to Go's {@code path.IsAbs}.
	 */
	private static Function isAbs() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return false;
			}
			return String.valueOf(args[0]).startsWith("/");
		};
	}

}
