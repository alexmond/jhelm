package org.alexmond.jhelm.gotemplate.sprig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.sprig.functions.CollectionFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.CryptoFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.DateFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.EncodingFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.LogicFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.MathFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.NetworkFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.PathFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.ReflectionFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.SemverFunctions;
import org.alexmond.jhelm.gotemplate.sprig.functions.StringFunctions;

/**
 * Centralized registry for all Sprig functions organized by category This replaces the
 * monolithic SprigFunctions class with a coordinated approach
 * <p>
 * Categories: - String functions: trim, upper, lower, replace, regex, etc. - Collection
 * functions: list, dict, append, merge, keys, values, etc. - Logic functions: default,
 * empty, coalesce, ternary, fail - Math functions: add, sub, mul, div, mod, etc. -
 * Encoding functions: b64enc, b64dec, sha256sum, etc. - Date/Time functions: now, date,
 * dateInZone, etc. - Type/Reflection functions: typeOf, kindOf, typeIs, kindIs - Crypto
 * functions: genPrivateKey, derivePassword, htpasswd, etc. - Network functions:
 * getHostByName - Semver functions: semver, semverCompare
 *
 * @see <a href="https://masterminds.github.io/sprig/">Sprig Documentation</a>
 */
public final class SprigFunctionsRegistry {

	private SprigFunctionsRegistry() {
	}

	/**
	 * Get all Sprig functions from all categories
	 * @return Map of function name to Function implementation
	 */
	public static Map<String, Function> getAllFunctions() {
		Map<String, Function> functions = new HashMap<>();

		// String manipulation functions
		functions.putAll(StringFunctions.getFunctions());

		// Collection functions (lists, dicts)
		functions.putAll(CollectionFunctions.getFunctions());

		// Logic and control flow
		functions.putAll(LogicFunctions.getFunctions());

		// Math and numeric operations
		functions.putAll(MathFunctions.getFunctions());

		// Encoding and hashing
		functions.putAll(EncodingFunctions.getFunctions());

		// Cryptography and random generation
		functions.putAll(CryptoFunctions.getFunctions());

		// Date and time operations
		functions.putAll(DateFunctions.getFunctions());

		// Type reflection and introspection
		functions.putAll(ReflectionFunctions.getFunctions());

		// Network operations
		functions.putAll(NetworkFunctions.getFunctions());

		// Path operations
		functions.putAll(PathFunctions.getFunctions());

		// Semantic versioning
		functions.putAll(SemverFunctions.getFunctions());

		return functions;
	}

	/**
	 * Get function categories for documentation
	 * @return Map of category name to list of function names
	 */
	public static Map<String, List<String>> getFunctionCategories() {
		Map<String, List<String>> categories = new HashMap<>();

		categories.put("Strings",
				List.of("trim", "trimAll", "trimall", "trimPrefix", "trimSuffix", "nospace", "upper", "lower", "title",
						"untitle", "swapcase", "repeat", "substr", "trunc", "abbrev", "abbrevboth", "initials", "wrap",
						"wrapWith", "contains", "hasPrefix", "hasSuffix", "quote", "squote", "cat", "indent", "nindent",
						"replace", "plural", "snakecase", "camelcase", "kebabcase", "shuffle", "regexMatch",
						"mustRegexMatch", "regexFind", "mustRegexFind", "regexFindAll", "mustRegexFindAll",
						"regexReplaceAll", "mustRegexReplaceAll", "regexReplaceAllLiteral",
						"mustRegexReplaceAllLiteral", "regexSplit", "mustRegexSplit", "regexQuoteMeta"));

		categories.put("Collections",
				List.of("list", "tuple", "first", "mustFirst", "rest", "mustRest", "last", "mustLast", "initial",
						"mustInitial", "append", "mustAppend", "push", "mustPush", "prepend", "mustPrepend", "concat",
						"reverse", "mustReverse", "uniq", "mustUniq", "without", "mustWithout", "has", "mustHas",
						"slice", "mustSlice", "chunk", "mustChunk", "until", "untilStep", "seq", "compact",
						"mustCompact", "sortAlpha", "split", "splitList", "splitn", "join", "dict", "get", "set",
						"unset", "hasKey", "mustHasKey", "pluck", "dig", "merge", "mergeOverwrite", "mustMerge",
						"mustMergeOverwrite", "keys", "mustKeys", "pick", "mustPick", "omit", "mustOmit", "values",
						"mustValues", "deepCopy", "mustDeepCopy"));

		categories.put("Logic", List.of("default", "empty", "coalesce", "ternary", "fail", "all", "any"));

		categories.put("Math", List.of("add", "add1", "sub", "mul", "div", "mod", "max", "min", "biggest", "floor",
				"ceil", "round", "addf", "add1f", "subf", "mulf", "divf", "maxf", "minf", "randInt"));

		categories.put("Encoding",
				List.of("b64enc", "b64dec", "b32enc", "b32dec", "sha1sum", "sha256sum", "sha512sum", "adler32sum"));

		categories.put("Crypto",
				List.of("genPrivateKey", "derivePassword", "genSignedCert", "genSelfSignedCert", "genCA",
						"genCAWithKey", "genSelfSignedCertWithKey", "genSignedCertWithKey", "buildCustomCert",
						"htpasswd", "bcrypt", "randAlphaNum", "randAlpha", "randNumeric", "randAscii", "randBytes",
						"encryptAES", "decryptAES", "uuidv4"));

		categories.put("Date",
				List.of("now", "date", "dateInZone", "date_in_zone", "dateModify", "date_modify", "mustDateModify",
						"must_date_modify", "htmlDate", "htmlDateInZone", "durationRound", "ago", "duration",
						"unixEpoch", "toDate", "mustToDate"));

		categories.put("Type/Reflection", List.of("typeOf", "kindOf", "typeIs", "kindIs", "typeIsLike", "deepEqual"));

		categories.put("Network", List.of("getHostByName", "urlParse", "urlJoin"));

		categories.put("Path",
				List.of("base", "dir", "ext", "clean", "isAbs", "osBase", "osDir", "osExt", "osClean", "osIsAbs"));

		categories.put("Semver", List.of("semver", "semverCompare"));

		return categories;
	}

}
