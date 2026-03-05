package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import lombok.extern.slf4j.Slf4j;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.FunctionExecutionException;

/**
 * Date and time manipulation functions from Sprig library. Includes date formatting,
 * parsing, and manipulation operations.
 *
 * @see <a href="https://masterminds.github.io/sprig/date.html">Sprig Date Functions</a>
 */
@Slf4j
public final class DateFunctions {

	private DateFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		// Current time
		functions.put("now", now());

		// Date formatting
		functions.put("date", date());
		functions.put("dateInZone", dateInZone());
		functions.put("htmlDate", htmlDate());
		functions.put("htmlDateInZone", htmlDateInZone());

		// Date parsing
		functions.put("toDate", toDate());
		functions.put("mustToDate", mustToDate());

		// Date manipulation
		functions.put("dateModify", dateModify());
		functions.put("mustDateModify", mustDateModify());
		functions.put("durationRound", durationRound());
		functions.put("ago", ago());
		functions.put("duration", duration());

		// Unix epoch
		functions.put("unixEpoch", unixEpoch());

		// Underscore aliases
		functions.put("date_in_zone", dateInZone());
		functions.put("date_modify", dateModify());
		functions.put("must_date_modify", mustDateModify());

		return functions;
	}

	// ========== Current Time Functions ==========

	/**
	 * Returns the current date/time.
	 */
	private static Function now() {
		return (args) -> Date.from(Instant.now());
	}

	// ========== Date Formatting Functions ==========

	/**
	 * Formats a date using the given layout. Go date format is converted to Java
	 * SimpleDateFormat.
	 */
	private static Function date() {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			String layout = String.valueOf(args[0]);
			Object dateObj = args[1];

			Date date = convertToDate(dateObj);
			if (date == null) {
				return "";
			}

			String javaLayout = convertGoLayoutToJava(layout);
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(javaLayout, Locale.ROOT);
				return sdf.format(date);
			}
			catch (Exception ex) {
				log.debug("date failed: {}", ex.getMessage());
				return "";
			}
		};
	}

	/**
	 * Formats a date using the given layout in a specific timezone.
	 */
	private static Function dateInZone() {
		return (args) -> {
			if (args.length < 3) {
				return "";
			}
			String layout = String.valueOf(args[0]);
			Object dateObj = args[1];
			String timezone = String.valueOf(args[2]);

			Date date = convertToDate(dateObj);
			if (date == null) {
				return "";
			}

			String javaLayout = convertGoLayoutToJava(layout);
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(javaLayout, Locale.ROOT);
				sdf.setTimeZone(TimeZone.getTimeZone(timezone));
				return sdf.format(date);
			}
			catch (Exception ex) {
				log.debug("dateInZone failed: {}", ex.getMessage());
				return "";
			}
		};
	}

	/**
	 * Formats a date in HTML date format (YYYY-MM-DD).
	 */
	private static Function htmlDate() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			Date date = convertToDate(args[0]);
			if (date == null) {
				return "";
			}

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
			return sdf.format(date);
		};
	}

	/**
	 * Formats a date in HTML date format (YYYY-MM-DD) in a specific timezone.
	 */
	private static Function htmlDateInZone() {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			Date date = convertToDate(args[0]);
			if (date == null) {
				return "";
			}

			String timezone = String.valueOf(args[1]);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
			sdf.setTimeZone(TimeZone.getTimeZone(timezone));
			return sdf.format(date);
		};
	}

	// ========== Date Parsing Functions ==========

	/**
	 * Parses a date string using the given layout.
	 * @return Date object or null on error
	 */
	private static Function toDate() {
		return (args) -> {
			if (args.length < 2) {
				return null;
			}
			String layout = String.valueOf(args[0]);
			String dateStr = String.valueOf(args[1]);

			String javaLayout = convertGoLayoutToJava(layout);
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(javaLayout, Locale.ROOT);
				return sdf.parse(dateStr);
			}
			catch (ParseException ex) {
				log.debug("toDate failed: {}", ex.getMessage());
				return null;
			}
		};
	}

	/**
	 * Parses a date string using the given layout. Throws an exception on error.
	 * @return Date object
	 * @throws RuntimeException if parsing fails
	 */
	private static Function mustToDate() {
		return (args) -> {
			if (args.length < 2) {
				throw new FunctionExecutionException("mustToDate: insufficient arguments");
			}
			String layout = String.valueOf(args[0]);
			String dateStr = String.valueOf(args[1]);

			String javaLayout = convertGoLayoutToJava(layout);
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(javaLayout, Locale.ROOT);
				return sdf.parse(dateStr);
			}
			catch (ParseException ex) {
				throw new FunctionExecutionException("mustToDate: failed to parse date: " + ex.getMessage(), ex);
			}
		};
	}

	// ========== Date Manipulation Functions ==========

	/**
	 * Modifies a date by adding or subtracting duration.
	 * <p>
	 * Simplified implementation - supports basic duration strings.
	 */
	private static Function dateModify() {
		return (args) -> {
			if (args.length < 2) {
				return null;
			}
			String modification = String.valueOf(args[0]);
			Date date = convertToDate(args[1]);
			if (date == null) {
				return null;
			}

			try {
				// Parse modification string (e.g., "+1h", "-30m", "+24h")
				long millisToAdd = parseDurationToMillis(modification);
				return new Date(date.getTime() + millisToAdd);
			}
			catch (Exception ex) {
				log.debug("dateModify failed: {}", ex.getMessage());
				return date;
			}
		};
	}

	private static Function mustDateModify() {
		return (args) -> {
			if (args.length < 2) {
				throw new FunctionExecutionException("mustDateModify: insufficient arguments");
			}
			String modification = String.valueOf(args[0]);
			Date date = convertToDate(args[1]);
			if (date == null) {
				throw new FunctionExecutionException("mustDateModify: invalid date");
			}
			try {
				long millisToAdd = parseDurationToMillis(modification);
				return new Date(date.getTime() + millisToAdd);
			}
			catch (Exception ex) {
				throw new FunctionExecutionException("mustDateModify: " + ex.getMessage(), ex);
			}
		};
	}

	private static Function ago() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}
			Date date = convertToDate(args[0]);
			if (date == null) {
				return "";
			}
			Duration d = Duration.between(date.toInstant(), Instant.now());
			return formatDuration(d);
		};
	}

	private static Function duration() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "0s";
			}
			long seconds;
			if (args[0] instanceof Number) {
				seconds = ((Number) args[0]).longValue();
			}
			else {
				try {
					seconds = Long.parseLong(String.valueOf(args[0]).trim());
				}
				catch (NumberFormatException ex) {
					log.debug("duration failed: {}", ex.getMessage());
					return "0s";
				}
			}
			return formatDuration(Duration.ofSeconds(seconds));
		};
	}

	/**
	 * Rounds a duration to the nearest unit.
	 * <p>
	 * Simplified implementation.
	 * @return rounded duration
	 */
	private static Function durationRound() {
		return (args) -> {
			if (args.length == 0) {
				return "0s";
			}
			Object durationObj = args[0];

			if (durationObj instanceof Duration) {
				Duration duration = (Duration) durationObj;
				return duration.getSeconds() + "s";
			}

			// Parse duration string (simplified)
			String durationStr = String.valueOf(durationObj);
			try {
				long millis = parseDurationToMillis(durationStr);
				long seconds = millis / 1000;
				return seconds + "s";
			}
			catch (Exception ex) {
				log.debug("durationRound failed: {}", ex.getMessage());
				return "0s";
			}
		};
	}

	// ========== Unix Epoch Functions ==========

	/**
	 * Returns the Unix epoch timestamp (seconds since Jan 1, 1970 UTC).
	 */
	private static Function unixEpoch() {
		return (args) -> {
			Date date;
			if (args.length == 0) {
				date = Date.from(Instant.now());
			}
			else {
				date = convertToDate(args[0]);
			}
			if (date == null) {
				return 0L;
			}
			return date.getTime() / 1000;
		};
	}

	// ========== Helper Methods ==========

	/**
	 * Converts Go date format to Java SimpleDateFormat. Go uses "2006-01-02 15:04:05"
	 * while Java uses "yyyy-MM-dd HH:mm:ss"
	 */
	private static String convertGoLayoutToJava(String goLayout) {
		return goLayout.replace("2006", "yyyy")
			.replace("06", "yy")
			.replace("01", "MM")
			.replace("02", "dd")
			.replace("15", "HH")
			.replace("04", "mm")
			.replace("05", "ss")
			.replace("MST", "zzz")
			.replace("PM", "a");
	}

	/**
	 * Converts various types to Date object.
	 */
	private static Date convertToDate(Object dateObj) {
		if (dateObj instanceof Date) {
			return (Date) dateObj;
		}
		else if (dateObj instanceof Number) {
			return new Date(((Number) dateObj).longValue());
		}
		else if (dateObj instanceof Instant) {
			return Date.from((Instant) dateObj);
		}
		return null;
	}

	private static String formatDuration(Duration d) {
		long totalSeconds = Math.abs(d.getSeconds());
		if (totalSeconds == 0) {
			return "0s";
		}
		StringBuilder sb = new StringBuilder();
		if (d.isNegative()) {
			sb.append('-');
		}
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0) {
			sb.append(hours).append('h');
		}
		if (minutes > 0) {
			sb.append(minutes).append('m');
		}
		if (seconds > 0) {
			sb.append(seconds).append('s');
		}
		return sb.toString();
	}

	/**
	 * Parses duration string to milliseconds. Supports formats like: "+1h", "-30m",
	 * "+24h30m", "1h30m45s"
	 */
	private static long parseDurationToMillis(String duration) {
		String dur = duration.trim();
		boolean negative = dur.startsWith("-");
		if (negative || dur.startsWith("+")) {
			dur = dur.substring(1);
		}

		long totalMillis = 0;
		StringBuilder number = new StringBuilder();

		for (int i = 0; i < dur.length(); i++) {
			char c = dur.charAt(i);
			if (Character.isDigit(c)) {
				number.append(c);
			}
			else {
				if (number.length() > 0) {
					long value = Long.parseLong(number.toString());
					long millisToAdd = switch (c) {
						case 'h' -> value * 3600000L;
						case 'm' -> value * 60000L;
						case 's' -> value * 1000L;
						case 'd' -> value * 86400000L;
						default -> 0L;
					};
					totalMillis += millisToAdd;
					number = new StringBuilder();
				}
			}
		}

		return (negative) ? -totalMillis : totalMillis;
	}

}
