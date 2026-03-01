package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Math and numeric conversion functions from Sprig library. Includes basic arithmetic,
 * type conversions, and rounding operations.
 *
 * @see <a href="https://masterminds.github.io/sprig/math.html">Sprig Math Functions</a>
 */
public final class MathFunctions {

	private MathFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		// Type conversions
		functions.put("int", toInt());
		functions.put("int64", toInt64());
		functions.put("float64", toFloat64());
		functions.put("atoi", atoi());
		functions.put("toString", toStringFunc());
		functions.put("toStrings", toStrings());
		functions.put("toDecimal", toDecimal());

		// Basic arithmetic
		functions.put("add", add());
		functions.put("sub", sub());
		functions.put("mul", mul());
		functions.put("div", div());
		functions.put("mod", mod());
		functions.put("add1", add1());

		// Floating point arithmetic
		functions.put("addf", addf());
		functions.put("mulf", mulf());
		functions.put("divf", divf());

		// Rounding operations
		functions.put("floor", floor());
		functions.put("ceil", ceil());
		functions.put("round", round());

		// Min/Max operations
		functions.put("max", max());
		functions.put("min", min());
		functions.put("maxf", maxf());
		functions.put("minf", minf());

		// Random
		functions.put("randInt", randInt());

		// Additional float ops
		functions.put("add1f", add1f());
		functions.put("subf", subf());

		// Aliases
		functions.put("biggest", max());

		return functions;
	}

	// ========== Type Conversion Functions ==========

	private static Function toInt() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 0;
			}
			if (args[0] instanceof Number) {
				return ((Number) args[0]).intValue();
			}
			try {
				return Integer.parseInt(String.valueOf(args[0]));
			}
			catch (NumberFormatException ex) {
				return 0;
			}
		};
	}

	private static Function toInt64() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 0L;
			}
			if (args[0] instanceof Number) {
				return ((Number) args[0]).longValue();
			}
			try {
				return Long.parseLong(String.valueOf(args[0]));
			}
			catch (NumberFormatException ex) {
				return 0L;
			}
		};
	}

	private static Function toFloat64() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 0.0;
			}
			if (args[0] instanceof Number) {
				return ((Number) args[0]).doubleValue();
			}
			try {
				return Double.parseDouble(String.valueOf(args[0]));
			}
			catch (NumberFormatException ex) {
				return 0.0;
			}
		};
	}

	private static Function toStringFunc() {
		return (args) -> (args.length == 0) ? "" : String.valueOf(args[0]);
	}

	private static Function atoi() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 0;
			}
			try {
				return Integer.parseInt(String.valueOf(args[0]).trim());
			}
			catch (NumberFormatException ex) {
				return 0;
			}
		};
	}

	private static Function toStrings() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return List.of();
			}
			Object arg = args[0];
			if (arg instanceof Collection<?> coll) {
				List<String> result = new ArrayList<>(coll.size());
				for (Object item : coll) {
					result.add(String.valueOf(item));
				}
				return result;
			}
			return List.of(String.valueOf(arg));
		};
	}

	private static Function toDecimal() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 0L;
			}
			String s = String.valueOf(args[0]).trim();
			try {
				return Long.parseLong(s, 8);
			}
			catch (NumberFormatException ex) {
				return 0L;
			}
		};
	}

	// ========== Basic Arithmetic Functions ==========

	/**
	 * Adds all numeric arguments together. Returns integer if result is whole number,
	 * otherwise double.
	 */
	private static Function add() {
		return (args) -> {
			if (args.length < 2) {
				return 0;
			}
			double sum = 0;
			for (Object arg : args) {
				if (arg instanceof Number) {
					sum += ((Number) arg).doubleValue();
				}
			}
			// Return long if result is whole number, otherwise double
			if (sum == Math.floor(sum)) {
				return (long) sum;
			}
			return sum;
		};
	}

	/**
	 * Subtracts subsequent arguments from the first argument. Returns integer if result
	 * is whole number, otherwise double.
	 */
	private static Function sub() {
		return (args) -> {
			if (args.length < 2) {
				return 0;
			}
			double result = ((Number) args[0]).doubleValue();
			for (int i = 1; i < args.length; i++) {
				if (args[i] instanceof Number) {
					result -= ((Number) args[i]).doubleValue();
				}
			}
			if (result == Math.floor(result)) {
				return (long) result;
			}
			return result;
		};
	}

	/**
	 * Multiplies all numeric arguments together. Returns integer if result is whole
	 * number, otherwise double.
	 */
	private static Function mul() {
		return (args) -> {
			if (args.length < 2) {
				return 0;
			}
			double product = ((Number) args[0]).doubleValue();
			for (int i = 1; i < args.length; i++) {
				if (args[i] instanceof Number) {
					product *= ((Number) args[i]).doubleValue();
				}
			}
			if (product == Math.floor(product)) {
				return (long) product;
			}
			return product;
		};
	}

	/**
	 * Performs integer division of first argument by second argument (truncating toward
	 * zero). Returns 0 if divisor is 0. Matches Go Sprig behavior where {@code div} is
	 * always integer division and {@code divf} is for floating point.
	 */
	private static Function div() {
		return (args) -> {
			if (args.length < 2) {
				return 0L;
			}
			long dividend = ((Number) args[0]).longValue();
			long divisor = ((Number) args[1]).longValue();
			if (divisor == 0) {
				return 0L;
			}
			return dividend / divisor;
		};
	}

	/**
	 * Returns modulo of a % b. Returns 0 if b is 0.
	 */
	private static Function mod() {
		return (args) -> {
			if (args.length < 2) {
				return 0L;
			}
			long a = ((Number) args[0]).longValue();
			long b = ((Number) args[1]).longValue();
			return (b != 0) ? a % b : 0L;
		};
	}

	/**
	 * Adds 1 to the given number. Preserves type (integer vs floating point).
	 */
	private static Function add1() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 1;
			}
			if (args[0] instanceof Number) {
				Number num = (Number) args[0];
				if (args[0] instanceof Double || args[0] instanceof Float) {
					return num.doubleValue() + 1;
				}
				else {
					return num.longValue() + 1;
				}
			}
			return 1;
		};
	}

	// ========== Floating Point Arithmetic Functions ==========

	/**
	 * Adds floating point numbers, always returns double.
	 */
	private static Function addf() {
		return (args) -> {
			if (args.length < 2) {
				return 0.0;
			}
			double result = 0;
			for (Object arg : args) {
				result += ((Number) arg).doubleValue();
			}
			return result;
		};
	}

	/**
	 * Multiplies floating point numbers, always returns double.
	 */
	private static Function mulf() {
		return (args) -> {
			if (args.length < 2) {
				return 0.0;
			}
			double result = ((Number) args[0]).doubleValue();
			for (int i = 1; i < args.length; i++) {
				result *= ((Number) args[i]).doubleValue();
			}
			return result;
		};
	}

	/**
	 * Divides floating point numbers, always returns double. Returns 0.0 if any divisor
	 * is 0.
	 */
	private static Function divf() {
		return (args) -> {
			if (args.length < 2) {
				return 0.0;
			}
			double result = ((Number) args[0]).doubleValue();
			for (int i = 1; i < args.length; i++) {
				double divisor = ((Number) args[i]).doubleValue();
				if (divisor != 0) {
					result /= divisor;
				}
				else {
					return 0.0;
				}
			}
			return result;
		};
	}

	// ========== Rounding Functions ==========

	/**
	 * Returns the largest integer value less than or equal to the given number.
	 */
	private static Function floor() {
		return (args) -> {
			if (args.length == 0) {
				return 0L;
			}
			Object val = args[0];
			if (val instanceof Number) {
				return (long) Math.floor(((Number) val).doubleValue());
			}
			return 0L;
		};
	}

	/**
	 * Returns the smallest integer value greater than or equal to the given number.
	 */
	private static Function ceil() {
		return (args) -> {
			if (args.length == 0) {
				return 0L;
			}
			Object val = args[0];
			if (val instanceof Number) {
				return (long) Math.ceil(((Number) val).doubleValue());
			}
			return 0L;
		};
	}

	/**
	 * Returns the nearest integer value, rounding half away from zero.
	 */
	private static Function round() {
		return (args) -> {
			if (args.length == 0) {
				return 0L;
			}
			Object val = args[0];
			if (val instanceof Number) {
				return Math.round(((Number) val).doubleValue());
			}
			return 0L;
		};
	}

	// ========== Min/Max Functions ==========

	/**
	 * Returns the maximum of all numeric arguments.
	 */
	private static Function max() {
		return (args) -> {
			if (args.length == 0) {
				return 0;
			}
			double maxVal = Double.NEGATIVE_INFINITY;
			for (Object arg : args) {
				if (arg instanceof Number) {
					double val = ((Number) arg).doubleValue();
					if (val > maxVal) {
						maxVal = val;
					}
				}
			}
			// Return long if result is whole number, otherwise double
			if (maxVal == Math.floor(maxVal) && !Double.isInfinite(maxVal)) {
				return (long) maxVal;
			}
			return maxVal;
		};
	}

	/**
	 * Returns the minimum of all numeric arguments.
	 */
	private static Function min() {
		return (args) -> {
			if (args.length == 0) {
				return 0;
			}
			double minVal = Double.POSITIVE_INFINITY;
			for (Object arg : args) {
				if (arg instanceof Number) {
					double val = ((Number) arg).doubleValue();
					if (val < minVal) {
						minVal = val;
					}
				}
			}
			if (minVal == Math.floor(minVal) && !Double.isInfinite(minVal)) {
				return (long) minVal;
			}
			return minVal;
		};
	}

	private static Function maxf() {
		return (args) -> {
			if (args.length == 0) {
				return 0.0;
			}
			double maxVal = Double.NEGATIVE_INFINITY;
			for (Object arg : args) {
				if (arg instanceof Number) {
					double val = ((Number) arg).doubleValue();
					if (val > maxVal) {
						maxVal = val;
					}
				}
			}
			return maxVal;
		};
	}

	private static Function minf() {
		return (args) -> {
			if (args.length == 0) {
				return 0.0;
			}
			double minVal = Double.POSITIVE_INFINITY;
			for (Object arg : args) {
				if (arg instanceof Number) {
					double val = ((Number) arg).doubleValue();
					if (val < minVal) {
						minVal = val;
					}
				}
			}
			return minVal;
		};
	}

	private static Function randInt() {
		return (args) -> {
			if (args.length < 2) {
				return 0;
			}
			int min = ((Number) args[0]).intValue();
			int max = ((Number) args[1]).intValue();
			if (min >= max) {
				return min;
			}
			return ThreadLocalRandom.current().nextInt(min, max);
		};
	}

	private static Function add1f() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 1.0;
			}
			if (args[0] instanceof Number) {
				return ((Number) args[0]).doubleValue() + 1.0;
			}
			return 1.0;
		};
	}

	private static Function subf() {
		return (args) -> {
			if (args.length < 2) {
				return 0.0;
			}
			double result = ((Number) args[0]).doubleValue();
			for (int i = 1; i < args.length; i++) {
				if (args[i] instanceof Number) {
					result -= ((Number) args[i]).doubleValue();
				}
			}
			return result;
		};
	}

}
