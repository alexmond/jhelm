package org.alexmond.jhelm.app.plugin;

import java.util.Locale;

/**
 * Maps the JVM's {@code os.name}/{@code os.arch} to the Go {@code GOOS}/{@code GOARCH}
 * spellings that Helm's {@code platformCommand} entries are keyed on, so a plugin's
 * per-platform command can be matched the same way {@code helm} matches it.
 */
public final class HelmPlatform {

	private HelmPlatform() {
	}

	/**
	 * The current {@code GOOS} value ({@code linux}, {@code darwin}, {@code windows}, …).
	 * @return the Go OS name for the running JVM
	 */
	public static String currentOs() {
		return goOs(System.getProperty("os.name", ""));
	}

	/**
	 * The current {@code GOARCH} value ({@code amd64}, {@code arm64}, {@code 386}, …).
	 * @return the Go architecture name for the running JVM
	 */
	public static String currentArch() {
		return goArch(System.getProperty("os.arch", ""));
	}

	static String goOs(String osName) {
		String os = osName.toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return "windows";
		}
		if (os.contains("mac") || os.contains("darwin")) {
			return "darwin";
		}
		if (os.contains("nux") || os.contains("nix")) {
			return "linux";
		}
		return os;
	}

	static String goArch(String osArch) {
		return switch (osArch.toLowerCase(Locale.ROOT)) {
			case "x86_64", "amd64" -> "amd64";
			case "aarch64", "arm64" -> "arm64";
			case "x86", "i386", "i486", "i586", "i686" -> "386";
			default -> osArch.toLowerCase(Locale.ROOT);
		};
	}

}
