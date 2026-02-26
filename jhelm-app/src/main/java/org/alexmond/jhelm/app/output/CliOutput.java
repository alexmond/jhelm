package org.alexmond.jhelm.app.output;

import picocli.CommandLine.Help.Ansi;

/**
 * Central CLI output utility providing ANSI color support.
 * <p>
 * Uses Picocli's {@link Ansi#AUTO} for terminal detection, which auto-detects TTY and
 * respects the {@code NO_COLOR} environment variable. Color can be disabled
 * programmatically via {@link #setEnabled(boolean)} (e.g. for {@code --no-color}).
 */
public final class CliOutput {

	private static boolean enabled = Ansi.AUTO.enabled();

	private CliOutput() {
	}

	/**
	 * Sets whether color output is enabled.
	 * @param enabled {@code true} to enable colors, {@code false} to disable
	 */
	public static void setEnabled(boolean enabled) {
		CliOutput.enabled = enabled;
	}

	/**
	 * Returns whether color output is currently enabled.
	 * @return {@code true} if colors are active
	 */
	public static boolean enabled() {
		return enabled;
	}

	/**
	 * Formats text as a success message (green).
	 * @param text the text to format
	 * @return the formatted string
	 */
	public static String success(String text) {
		return colorize("@|green " + text + "|@");
	}

	/**
	 * Formats text as an error message (red).
	 * @param text the text to format
	 * @return the formatted string
	 */
	public static String error(String text) {
		return colorize("@|red " + text + "|@");
	}

	/**
	 * Formats text as a warning message (yellow).
	 * @param text the text to format
	 * @return the formatted string
	 */
	public static String warn(String text) {
		return colorize("@|yellow " + text + "|@");
	}

	/**
	 * Formats text as an informational message (cyan).
	 * @param text the text to format
	 * @return the formatted string
	 */
	public static String info(String text) {
		return colorize("@|cyan " + text + "|@");
	}

	/**
	 * Formats text as bold.
	 * @param text the text to format
	 * @return the formatted string
	 */
	public static String bold(String text) {
		return colorize("@|bold " + text + "|@");
	}

	/**
	 * Formats text as a header (bold).
	 * @param text the text to format
	 * @return the formatted string
	 */
	public static String header(String text) {
		return bold(text);
	}

	/**
	 * Prints a line to stdout.
	 * @param text the text to print
	 */
	public static void println(String text) {
		System.out.println(text);
	}

	/**
	 * Prints a formatted string to stdout.
	 * @param format the format string
	 * @param args the arguments
	 */
	public static void printf(String format, Object... args) {
		System.out.printf(format, args);
	}

	/**
	 * Prints an error line to stderr.
	 * @param text the text to print
	 */
	public static void errPrintln(String text) {
		System.err.println(text);
	}

	private static String colorize(String markup) {
		if (enabled) {
			return Ansi.ON.string(markup);
		}
		return Ansi.OFF.string(markup);
	}

}
