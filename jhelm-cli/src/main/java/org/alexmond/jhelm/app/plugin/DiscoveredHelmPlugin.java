package org.alexmond.jhelm.app.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Helm plugin found on disk: its name, its installed directory, and its parsed
 * {@link HelmPluginManifest}. Also resolves the manifest's {@code command} /
 * {@code platformCommand} into a concrete argument vector for the current platform,
 * expanding environment references such as {@code $HELM_PLUGIN_DIR}.
 *
 * @param name the plugin name (the {@code helm <name>} command)
 * @param directory the plugin's installed directory
 * @param manifest the parsed {@code plugin.yaml}
 */
public record DiscoveredHelmPlugin(String name, Path directory, HelmPluginManifest manifest) {

	/**
	 * Resolves the command to execute for the current OS/architecture, expanding
	 * environment references against {@code env} and splitting into an argument vector.
	 * The most specific {@code platformCommand} entry wins (exact
	 * {@code os}+{@code arch}, then {@code os} with an unspecified {@code arch});
	 * otherwise the top-level {@code command} is used.
	 * @param os the Go {@code GOOS} value to match (see {@link HelmPlatform#currentOs()})
	 * @param arch the Go {@code GOARCH} value to match
	 * ({@link HelmPlatform#currentArch()})
	 * @param env the environment used to expand {@code $VAR}/{@code ${VAR}} references
	 * @return the argument vector, or an empty list if the manifest declares no command
	 */
	public List<String> resolveCommand(String os, String arch, Map<String, String> env) {
		String raw = selectCommand(os, arch);
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return tokenize(expand(raw, env));
	}

	private String selectCommand(String os, String arch) {
		String osOnly = null;
		for (HelmPluginManifest.PlatformCommand platform : this.manifest.getPlatformCommand()) {
			if (!matchesOs(platform.getOs(), os)) {
				continue;
			}
			if (matchesArch(platform.getArch(), arch)) {
				return platform.getCommand();
			}
			if (isBlank(platform.getArch()) && osOnly == null) {
				osOnly = platform.getCommand();
			}
		}
		return (osOnly != null) ? osOnly : this.manifest.getCommand();
	}

	private static boolean matchesOs(String declared, String os) {
		return isBlank(declared) || declared.equalsIgnoreCase(os);
	}

	private static boolean matchesArch(String declared, String arch) {
		return !isBlank(declared) && declared.equalsIgnoreCase(arch);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/** Expands {@code $NAME} and {@code ${NAME}} references against the environment. */
	static String expand(String input, Map<String, String> env) {
		StringBuilder out = new StringBuilder(input.length());
		int i = 0;
		while (i < input.length()) {
			char c = input.charAt(i);
			if (c != '$') {
				out.append(c);
				i++;
				continue;
			}
			int start = i + 1;
			if (start < input.length() && input.charAt(start) == '{') {
				int end = input.indexOf('}', start + 1);
				if (end > 0) {
					out.append(lookup(input.substring(start + 1, end), env));
					i = end + 1;
					continue;
				}
			}
			int end = start;
			while (end < input.length() && (Character.isLetterOrDigit(input.charAt(end)) || input.charAt(end) == '_')) {
				end++;
			}
			if (end > start) {
				out.append(lookup(input.substring(start, end), env));
				i = end;
			}
			else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	private static String lookup(String name, Map<String, String> env) {
		String value = env.get(name);
		return (value != null) ? value : "";
	}

	/** Splits a command line into arguments on whitespace, honoring simple quoting. */
	static List<String> tokenize(String command) {
		List<String> args = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inArg = false;
		char quote = 0;
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
				else {
					current.append(c);
				}
				continue;
			}
			if (c == '"' || c == '\'') {
				quote = c;
				inArg = true;
			}
			else if (Character.isWhitespace(c)) {
				if (inArg) {
					args.add(current.toString());
					current.setLength(0);
					inArg = false;
				}
			}
			else {
				current.append(c);
				inArg = true;
			}
		}
		if (inArg) {
			args.add(current.toString());
		}
		return args;
	}

}
