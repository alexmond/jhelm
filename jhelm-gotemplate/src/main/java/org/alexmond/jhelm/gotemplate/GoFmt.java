package org.alexmond.jhelm.gotemplate;

import java.math.BigDecimal;

/**
 * Formats values the way Go's {@code fmt} package does, so template output matches
 * {@code helm template}. Helm loads chart values via JSON, where every number is a
 * {@code float64}; rendering such a value to text (directly, or through {@code quote},
 * {@code toString}, {@code printf %v}, {@code print}, …) therefore uses Go's float
 * formatting, which switches to scientific notation for magnitudes {@code >= 1e6} (and
 * {@code < 1e-4}). Integer types ({@code int} results of
 * {@code int}/{@code len}/literals) render as plain decimals.
 *
 * <p>
 * This is distinct from {@code toYaml}, whose numbers follow {@code sigs.k8s.io/yaml}
 * (JSON) formatting and never use scientific notation; that path must not call here.
 */
public final class GoFmt {

	private GoFmt() {
	}

	/**
	 * Formats a number the way Go's {@code fmt.Sprint} would: a floating-point value
	 * ({@link Double}/{@link Float}) uses {@link #floatString}, any other number renders
	 * as a plain decimal.
	 * @param number the number
	 * @return the Go-formatted string
	 */
	public static String number(Number number) {
		if (number instanceof Double d) {
			return floatString(d);
		}
		if (number instanceof Float f) {
			return floatString(f);
		}
		return String.valueOf(number);
	}

	/**
	 * Renders a {@code double} the way Go's {@code fmt} prints a {@code float64} (the
	 * shortest %g form): scientific notation when the decimal exponent is {@code < -4} or
	 * {@code >= 6} (e.g. {@code 1000000 -> 1e+06}, {@code 0.00001 -> 1e-05}), otherwise a
	 * plain decimal with no trailing zeros ({@code 3.0 -> 3}, {@code 999999 -> 999999}).
	 * @param d the value
	 * @return the Go-formatted string
	 */
	public static String floatString(double d) {
		if (Double.isNaN(d)) {
			return "NaN";
		}
		if (Double.isInfinite(d)) {
			return (d > 0) ? "+Inf" : "-Inf";
		}
		if (d == 0.0) {
			return "0";
		}
		String sign = (d < 0) ? "-" : "";
		// Double.toString yields the shortest round-tripping decimal; BigDecimal
		// preserves
		// those exact digits, and stripTrailingZeros normalises 1000000.0 -> 1E+6 etc.
		BigDecimal bd = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
		String digits = bd.unscaledValue().toString();
		int exp = bd.precision() - bd.scale() - 1;
		if (exp < -4 || exp >= 6) {
			String mantissa = (digits.length() == 1) ? digits : digits.charAt(0) + "." + digits.substring(1);
			String expSign = (exp < 0) ? "-" : "+";
			String absExp = Integer.toString(Math.abs(exp));
			return sign + mantissa + "e" + expSign + ((absExp.length() < 2) ? "0" + absExp : absExp);
		}
		return sign + bd.toPlainString();
	}

}
