package org.alexmond.jhelm.gotemplate.helm.functions;

import java.util.HashMap;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.FunctionExecutionException;

/**
 * Helm 4 duration helper functions, ported from {@code pkg/engine/funcs.go}.
 * <p>
 * Every helper coerces its argument to a {@code time.Duration} (nanoseconds) the way Helm
 * does: a number is taken as <em>seconds</em>; a string is parsed first as a Go duration
 * ({@code "1m30s"}, {@code "90s"}, {@code "36h"}) and, failing that, as a floating-point
 * number of seconds ({@code "2.5"}); anything unparseable becomes the zero duration (the
 * {@code must} variant errors instead). The component accessors ({@code durationSeconds},
 * {@code durationMinutes}, …) return Go's {@code float64}/{@code int64} component values,
 * and {@code mustToDuration}/{@code durationRoundTo}/{@code durationTruncateTo} render
 * Go's {@code time.Duration.String()} form.
 */
public final class DurationFunctions {

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private static final long NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND;

	private static final long NANOS_PER_HOUR = 60L * NANOS_PER_MINUTE;

	private static final Map<String, Long> UNIT_NANOS = Map.of("ns", 1L, "us", 1_000L, "µs", 1_000L, "ms", 1_000_000L,
			"s", NANOS_PER_SECOND, "m", NANOS_PER_MINUTE, "h", NANOS_PER_HOUR);

	private DurationFunctions() {
	}

	/**
	 * Get the Helm 4 duration helper functions.
	 * @return map of function name to implementation
	 */
	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		functions.put("mustToDuration", (args) -> goDurationString(toDurationStrict(arg(args, 0))));

		functions.put("durationSeconds", (args) -> (double) toDurationLenient(arg(args, 0)) / NANOS_PER_SECOND);
		functions.put("durationMinutes", (args) -> (double) toDurationLenient(arg(args, 0)) / NANOS_PER_MINUTE);
		functions.put("durationHours", (args) -> (double) toDurationLenient(arg(args, 0)) / NANOS_PER_HOUR);
		functions.put("durationDays", (args) -> (double) toDurationLenient(arg(args, 0)) / (24L * NANOS_PER_HOUR));
		functions.put("durationWeeks",
				(args) -> (double) toDurationLenient(arg(args, 0)) / (7L * 24L * NANOS_PER_HOUR));

		functions.put("durationMilliseconds", (args) -> toDurationLenient(arg(args, 0)) / 1_000_000L);
		functions.put("durationMicroseconds", (args) -> toDurationLenient(arg(args, 0)) / 1_000L);
		functions.put("durationNanoseconds", (args) -> toDurationLenient(arg(args, 0)));

		functions.put("durationRoundTo", (args) -> goDurationString(
				roundDuration(toDurationLenient(arg(args, 0)), toDurationLenient(arg(args, 1)))));
		functions.put("durationTruncateTo", (args) -> goDurationString(
				truncateDuration(toDurationLenient(arg(args, 0)), toDurationLenient(arg(args, 1)))));

		return functions;
	}

	private static Object arg(Object[] args, int index) {
		return (args != null && args.length > index) ? args[index] : null;
	}

	/**
	 * Coerce to nanoseconds, returning the zero duration on any parse failure (the
	 * lenient {@code duration*} accessors).
	 */
	private static long toDurationLenient(Object value) {
		Long nanos = toDurationOrNull(value);
		return (nanos != null) ? nanos : 0L;
	}

	/**
	 * Coerce to nanoseconds, throwing on a parse failure ({@code mustToDuration}).
	 */
	private static long toDurationStrict(Object value) {
		Long nanos = toDurationOrNull(value);
		if (nanos == null) {
			throw new FunctionExecutionException("mustToDuration: cannot parse duration: " + value);
		}
		return nanos;
	}

	private static Long toDurationOrNull(Object value) {
		if (value instanceof Number num) {
			return Math.round(num.doubleValue() * NANOS_PER_SECOND);
		}
		if (value instanceof String str) {
			String trimmed = str.trim();
			Long parsed = parseGoDuration(trimmed);
			if (parsed != null) {
				return parsed;
			}
			try {
				return Math.round(Double.parseDouble(trimmed) * NANOS_PER_SECOND);
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Parse a Go {@code time.ParseDuration} string (e.g. {@code "1m30s"}). Returns the
	 * duration in nanoseconds, or {@code null} if the input is not a valid Go duration (a
	 * bare number such as {@code "2.5"} has no unit and so returns {@code null}, leaving
	 * the caller to try a float-seconds parse).
	 */
	private static Long parseGoDuration(String s) {
		if (s.isEmpty()) {
			return null;
		}
		boolean negative = false;
		int i = 0;
		char first = s.charAt(0);
		if (first == '+' || first == '-') {
			negative = first == '-';
			i = 1;
		}
		if (s.substring(i).equals("0")) {
			return 0L;
		}
		double nanos = 0;
		boolean sawUnit = false;
		while (i < s.length()) {
			int numStart = i;
			while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
				i++;
			}
			if (i == numStart) {
				return null;
			}
			double magnitude;
			try {
				magnitude = Double.parseDouble(s.substring(numStart, i));
			}
			catch (NumberFormatException ex) {
				return null;
			}
			int unitStart = i;
			while (i < s.length() && !Character.isDigit(s.charAt(i)) && s.charAt(i) != '.') {
				i++;
			}
			if (i == unitStart) {
				return null;
			}
			Long unit = UNIT_NANOS.get(s.substring(unitStart, i));
			if (unit == null) {
				return null;
			}
			nanos += magnitude * unit;
			sawUnit = true;
		}
		if (!sawUnit) {
			return null;
		}
		long result = Math.round(nanos);
		return negative ? -result : result;
	}

	/**
	 * Round to the nearest multiple of {@code m}, matching Go's {@code Duration.Round}
	 * (ties away from zero; a non-positive {@code m} returns {@code d} unchanged).
	 */
	static long roundDuration(long d, long m) {
		if (m <= 0) {
			return d;
		}
		long r = d % m;
		if (r + r < m) {
			return d - r;
		}
		return d + m - r;
	}

	/**
	 * Truncate toward zero to a multiple of {@code m}, matching Go's
	 * {@code Duration.Truncate} (a non-positive {@code m} returns {@code d} unchanged).
	 */
	static long truncateDuration(long d, long m) {
		if (m <= 0) {
			return d;
		}
		return d - d % m;
	}

	/**
	 * Render nanoseconds the way Go's {@code time.Duration.String()} does (e.g.
	 * {@code 90s ->
	 * "1m30s"}, {@code 0 -> "0s"}). Ported from the Go runtime.
	 */
	static String goDurationString(long d) {
		char[] buf = new char[32];
		int w = buf.length;
		boolean negative = d < 0;
		long u = negative ? -d : d;
		if (u < NANOS_PER_SECOND) {
			buf[--w] = 's';
			w--;
			if (u == 0) {
				return "0s";
			}
			int prec;
			if (u < 1_000L) {
				prec = 0;
				buf[w] = 'n';
			}
			else if (u < 1_000_000L) {
				prec = 3;
				buf[w] = 'µ';
			}
			else {
				prec = 6;
				buf[w] = 'm';
			}
			long[] holder = { u };
			w = fmtFrac(buf, w, holder, prec);
			w = fmtInt(buf, w, holder[0]);
		}
		else {
			buf[--w] = 's';
			long[] holder = { u };
			w = fmtFrac(buf, w, holder, 9);
			w = fmtInt(buf, w, holder[0] % 60);
			long rem = holder[0] / 60;
			if (rem > 0) {
				buf[--w] = 'm';
				w = fmtInt(buf, w, rem % 60);
				rem /= 60;
				if (rem > 0) {
					buf[--w] = 'h';
					w = fmtInt(buf, w, rem);
				}
			}
		}
		if (negative) {
			buf[--w] = '-';
		}
		return new String(buf, w, buf.length - w);
	}

	private static int fmtFrac(char[] buf, int w, long[] holder, int prec) {
		long v = holder[0];
		boolean print = false;
		for (int i = 0; i < prec; i++) {
			long digit = v % 10;
			print = print || digit != 0;
			if (print) {
				buf[--w] = (char) ('0' + digit);
			}
			v /= 10;
		}
		if (print) {
			buf[--w] = '.';
		}
		holder[0] = v;
		return w;
	}

	private static int fmtInt(char[] buf, int w, long v) {
		if (v == 0) {
			buf[--w] = '0';
		}
		else {
			while (v > 0) {
				buf[--w] = (char) ('0' + v % 10);
				v /= 10;
			}
		}
		return w;
	}

}
