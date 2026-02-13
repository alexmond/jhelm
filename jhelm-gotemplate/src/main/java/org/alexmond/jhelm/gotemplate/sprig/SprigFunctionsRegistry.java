package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.sprig.collections.CollectionFunctions;
import org.alexmond.jhelm.gotemplate.sprig.crypto.CryptoFunctions;
import org.alexmond.jhelm.gotemplate.sprig.date.DateFunctions;
import org.alexmond.jhelm.gotemplate.sprig.encoding.EncodingFunctions;
import org.alexmond.jhelm.gotemplate.sprig.logic.LogicFunctions;
import org.alexmond.jhelm.gotemplate.sprig.math.MathFunctions;
import org.alexmond.jhelm.gotemplate.sprig.network.NetworkFunctions;
import org.alexmond.jhelm.gotemplate.sprig.reflection.ReflectionFunctions;
import org.alexmond.jhelm.gotemplate.sprig.semver.SemverFunctions;
import org.alexmond.jhelm.gotemplate.sprig.strings.StringFunctions;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized registry for all Sprig functions organized by category
 * This replaces the monolithic SprigFunctions class with a coordinated approach
 * <p>
 * Categories:
 * - String functions: trim, upper, lower, replace, regex, etc.
 * - Collection functions: list, dict, append, merge, keys, values, etc.
 * - Logic functions: default, empty, coalesce, ternary, fail
 * - Math functions: add, sub, mul, div, mod, etc.
 * - Encoding functions: b64enc, b64dec, sha256sum, etc.
 * - Date/Time functions: now, date, dateInZone, etc.
 * - Type/Reflection functions: typeOf, kindOf, typeIs, kindIs
 * - Crypto functions: genPrivateKey, derivePassword, htpasswd, etc.
 * - Network functions: getHostByName
 * - Semver functions: semver, semverCompare
 *
 * @see <a href="https://masterminds.github.io/sprig/">Sprig Documentation</a>
 */
public class SprigFunctionsRegistry {

    /**
     * Get all Sprig functions from all categories
     *
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

        // Semantic versioning
        functions.putAll(SemverFunctions.getFunctions());

        // Legacy functions (if any remain that weren't refactored)
        functions.putAll(SprigFunctionsLegacy.getFunctions());

        return functions;
    }

    /**
     * Get function categories for documentation
     *
     * @return Map of category name to list of function names
     */
    public static Map<String, java.util.List<String>> getFunctionCategories() {
        Map<String, java.util.List<String>> categories = new HashMap<>();

        categories.put("Strings", java.util.List.of(
                "trim", "trimAll", "trimPrefix", "trimSuffix",
                "upper", "lower", "title", "untitle",
                "repeat", "substr", "trunc", "abbrev", "abbrevboth",
                "initials", "wrap", "wrapWith",
                "contains", "hasPrefix", "hasSuffix",
                "quote", "squote", "cat",
                "indent", "nindent", "replace",
                "plural", "snakecase", "camelcase", "kebabcase",
                "shuffle",
                "regexMatch", "mustRegexMatch", "regexFind", "mustRegexFind",
                "regexFindAll", "regexReplaceAll", "mustRegexReplaceAll",
                "regexReplaceAllLiteral", "mustRegexReplaceAllLiteral",
                "regexSplit", "mustRegexSplit"
        ));

        categories.put("Collections", java.util.List.of(
                "list", "first", "mustFirst", "rest", "mustRest",
                "last", "mustLast", "initial", "mustInitial",
                "append", "mustAppend", "prepend", "mustPrepend",
                "concat", "reverse", "mustReverse",
                "uniq", "mustUniq", "without", "mustWithout",
                "has", "mustHas", "slice", "mustSlice",
                "until", "untilStep", "seq",
                "compact", "mustCompact", "sortAlpha",
                "split", "splitList", "splitn", "join",
                "dict", "get", "set", "unset",
                "hasKey", "mustHasKey", "pluck", "dig",
                "merge", "mergeOverwrite", "mustMerge", "mustMergeOverwrite",
                "keys", "mustKeys", "pick", "mustPick",
                "omit", "mustOmit", "values", "mustValues",
                "deepCopy", "mustDeepCopy"
        ));

        categories.put("Logic", java.util.List.of(
                "default", "empty", "coalesce", "ternary", "fail"
        ));

        categories.put("Math", java.util.List.of(
                "add", "add1", "sub", "mul", "div", "mod",
                "max", "min", "floor", "ceil", "round"
        ));

        categories.put("Encoding", java.util.List.of(
                "b64enc", "b64dec", "b32enc", "b32dec",
                "sha1sum", "sha256sum", "sha512sum",
                "adler32sum"
        ));

        categories.put("Crypto", java.util.List.of(
                "genPrivateKey", "derivePassword", "genSignedCert",
                "genSelfSignedCert", "genCA", "buildCustomCert",
                "htpasswd", "randAlphaNum", "randAlpha",
                "randNumeric", "randAscii"
        ));

        categories.put("Date", java.util.List.of(
                "now", "date", "dateInZone", "dateModify",
                "htmlDate", "htmlDateInZone", "durationRound",
                "unixEpoch", "toDate", "mustToDate"
        ));

        categories.put("Type/Reflection", java.util.List.of(
                "typeOf", "kindOf", "typeIs", "kindIs",
                "typeIsLike", "deepEqual"
        ));

        categories.put("Network", java.util.List.of(
                "getHostByName"
        ));

        categories.put("Semver", java.util.List.of(
                "semver", "semverCompare"
        ));

        return categories;
    }
}
