package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.Function;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sprig String manipulation functions
 * Based on: <a href="https://masterminds.github.io/sprig/strings.html">https://masterminds.github.io/sprig/strings.html</a>
 */
public class StringFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // Basic string operations
        functions.put("trim", trim());
        functions.put("trimAll", trimAll());
        functions.put("trimPrefix", trimPrefix());
        functions.put("trimSuffix", trimSuffix());
        functions.put("upper", upper());
        functions.put("lower", lower());
        functions.put("title", title());
        functions.put("untitle", untitle());
        functions.put("repeat", repeat());
        functions.put("substr", substr());
        functions.put("trunc", trunc());
        functions.put("abbrev", abbrev());
        functions.put("abbrevboth", abbrevboth());
        functions.put("initials", initials());
        functions.put("wrap", wrap());
        functions.put("wrapWith", wrapWith());
        functions.put("contains", contains());
        functions.put("hasPrefix", hasPrefix());
        functions.put("hasSuffix", hasSuffix());
        functions.put("quote", quote());
        functions.put("squote", squote());
        functions.put("cat", cat());
        functions.put("indent", indent());
        functions.put("nindent", nindent());
        functions.put("replace", replace());
        functions.put("plural", plural());
        functions.put("snakecase", snakecase());
        functions.put("camelcase", camelcase());
        functions.put("kebabcase", kebabcase());
        functions.put("shuffle", shuffle());

        // Regex functions
        functions.put("regexMatch", regexMatch());
        functions.put("mustRegexMatch", mustRegexMatch());
        functions.put("regexFindAll", regexFindAll());
        functions.put("regexFind", regexFind());
        functions.put("mustRegexFind", mustRegexFind());
        functions.put("regexReplaceAll", regexReplaceAll());
        functions.put("mustRegexReplaceAll", mustRegexReplaceAll());
        functions.put("regexReplaceAllLiteral", regexReplaceAllLiteral());
        functions.put("mustRegexReplaceAllLiteral", mustRegexReplaceAllLiteral());
        functions.put("regexSplit", regexSplit());
        functions.put("mustRegexSplit", mustRegexSplit());

        return functions;
    }

    private static Function trim() {
        return args -> args.length == 0 ? "" : String.valueOf(args[0]).trim();
    }

    private static Function trimAll() {
        return args -> {
            if (args.length < 2) return "";
            String cutset = String.valueOf(args[0]);
            String s = String.valueOf(args[1]);
            return s.replaceAll("^[" + Pattern.quote(cutset) + "]+|[" + Pattern.quote(cutset) + "]+$", "");
        };
    }

    private static Function trimPrefix() {
        return args -> {
            if (args.length < 2) return "";
            String prefix = String.valueOf(args[0]);
            String text = String.valueOf(args[1]);
            return text.startsWith(prefix) ? text.substring(prefix.length()) : text;
        };
    }

    private static Function trimSuffix() {
        return args -> {
            if (args.length < 2) return "";
            String suffix = String.valueOf(args[0]);
            String text = String.valueOf(args[1]);
            return text.endsWith(suffix) ? text.substring(0, text.length() - suffix.length()) : text;
        };
    }

    private static Function upper() {
        return args -> args.length == 0 ? "" : String.valueOf(args[0]).toUpperCase();
    }

    private static Function lower() {
        return args -> args.length == 0 ? "" : String.valueOf(args[0]).toLowerCase();
    }

    private static Function title() {
        return args -> {
            if (args.length == 0) return "";
            String s = String.valueOf(args[0]);
            if (s.isEmpty()) return s;
            return Arrays.stream(s.split("\\s+"))
                    .map(word -> word.isEmpty() ? word :
                            Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        };
    }

    private static Function untitle() {
        return args -> {
            if (args.length == 0) return "";
            String s = String.valueOf(args[0]);
            if (s.isEmpty()) return s;
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        };
    }

    private static Function repeat() {
        return args -> {
            if (args.length < 2) return "";
            int count = ((Number) args[0]).intValue();
            String s = String.valueOf(args[1]);
            return s.repeat(Math.max(0, count));
        };
    }

    private static Function substr() {
        return args -> {
            if (args.length < 3) return "";
            int start = ((Number) args[0]).intValue();
            int end = ((Number) args[1]).intValue();
            String s = String.valueOf(args[2]);
            if (start < 0) start = 0;
            if (end > s.length()) end = s.length();
            if (start > end) return "";
            return s.substring(start, end);
        };
    }

    private static Function trunc() {
        return args -> {
            if (args.length < 2) return "";
            int len = ((Number) args[0]).intValue();
            String s = String.valueOf(args[1]);
            return s.length() > len ? s.substring(0, len) : s;
        };
    }

    private static Function abbrev() {
        return args -> {
            if (args.length < 2) return "";
            int max = ((Number) args[0]).intValue();
            String s = String.valueOf(args[1]);
            if (s.length() <= max) return s;
            return s.substring(0, max - 3) + "...";
        };
    }

    private static Function abbrevboth() {
        return args -> {
            if (args.length < 3) return "";
            int left = ((Number) args[0]).intValue();
            int right = ((Number) args[1]).intValue();
            String s = String.valueOf(args[2]);
            if (s.length() <= left + right) return s;
            return "..." + s.substring(s.length() - right);
        };
    }

    private static Function initials() {
        return args -> {
            if (args.length == 0) return "";
            String s = String.valueOf(args[0]);
            return Arrays.stream(s.split("\\s+"))
                    .filter(word -> !word.isEmpty())
                    .map(word -> String.valueOf(word.charAt(0)).toUpperCase())
                    .collect(Collectors.joining(""));
        };
    }

    private static Function wrap() {
        return args -> {
            if (args.length < 2) return "";
            int col = ((Number) args[0]).intValue();
            String s = String.valueOf(args[1]);
            return wrapText(s, col, "");
        };
    }

    private static Function wrapWith() {
        return args -> {
            if (args.length < 3) return "";
            int col = ((Number) args[0]).intValue();
            String indent = String.valueOf(args[1]);
            String s = String.valueOf(args[2]);
            return wrapText(s, col, indent);
        };
    }

    private static String wrapText(String text, int col, String indent) {
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder(indent);

        for (String word : words) {
            if (line.length() + word.length() + 1 > col && line.length() > indent.length()) {
                result.append(line).append("\n");
                line = new StringBuilder(indent);
            }
            if (line.length() > indent.length()) {
                line.append(" ");
            }
            line.append(word);
        }
        if (line.length() > indent.length()) {
            result.append(line);
        }
        return result.toString();
    }

    private static Function contains() {
        return args -> args.length >= 2 && String.valueOf(args[1]).contains(String.valueOf(args[0]));
    }

    private static Function hasPrefix() {
        return args -> args.length >= 2 && String.valueOf(args[1]).startsWith(String.valueOf(args[0]));
    }

    private static Function hasSuffix() {
        return args -> args.length >= 2 && String.valueOf(args[1]).endsWith(String.valueOf(args[0]));
    }

    private static Function quote() {
        return args -> args.length == 0 ? "\"\"" : "\"" + args[0] + "\"";
    }

    private static Function squote() {
        return args -> args.length == 0 ? "''" : "'" + args[0] + "'";
    }

    private static Function cat() {
        return args -> Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(" "));
    }

    private static Function indent() {
        return args -> {
            if (args.length < 2) return "";
            int spaces = ((Number) args[0]).intValue();
            String text = String.valueOf(args[1]);
            String indentStr = " ".repeat(spaces);
            // Indent all lines including the first one
            return indentStr + text.replace("\n", "\n" + indentStr);
        };
    }

    private static Function nindent() {
        return args -> {
            if (args.length < 2) return "";
            int spaces = ((Number) args[0]).intValue();
            String text = String.valueOf(args[1]);
            return "\n" + " ".repeat(spaces) + text.replace("\n", "\n" + " ".repeat(spaces));
        };
    }

    private static Function replace() {
        return args -> {
            if (args.length < 3) return "";
            String old = String.valueOf(args[0]);
            String newStr = String.valueOf(args[1]);
            String text = String.valueOf(args[2]);

            // Handle common escape sequences that appear as literals in templates
            old = unescapeString(old);
            newStr = unescapeString(newStr);

            return text.replace(old, newStr);
        };
    }

    /**
     * Convert common escape sequences from literal strings to actual characters.
     * In Go templates, string literals like "\n" are passed as literal backslash-n,
     * but functions like replace need to treat them as the actual escape character.
     */
    private static String unescapeString(String s) {
        return s.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");
    }

    private static Function plural() {
        return args -> {
            if (args.length < 3) return "";
            String singular = String.valueOf(args[0]);
            String plural = String.valueOf(args[1]);
            int count = ((Number) args[2]).intValue();
            return count == 1 ? singular : plural;
        };
    }

    private static Function snakecase() {
        return args -> {
            if (args.length == 0) return "";
            String s = String.valueOf(args[0]);
            return s.replaceAll("([a-z])([A-Z])", "$1_$2")
                    .replaceAll("\\s+", "_")
                    .toLowerCase();
        };
    }

    private static Function camelcase() {
        return args -> {
            if (args.length == 0) return "";
            String s = String.valueOf(args[0]);
            String[] parts = s.split("[\\s_-]+");
            return parts[0].toLowerCase() +
                    Arrays.stream(parts).skip(1)
                            .map(p -> p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase())
                            .collect(Collectors.joining(""));
        };
    }

    private static Function kebabcase() {
        return args -> {
            if (args.length == 0) return "";
            String s = String.valueOf(args[0]);
            return s.replaceAll("([a-z])([A-Z])", "$1-$2")
                    .replaceAll("[\\s_]+", "-")
                    .toLowerCase();
        };
    }

    private static Function shuffle() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            String s = String.valueOf(args[0]);
            List<Character> chars = new ArrayList<>();
            for (char c : s.toCharArray()) chars.add(c);
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            return sb.toString();
        };
    }

    // Regex functions
    private static Function regexMatch() {
        return args -> {
            try {
                return args.length >= 2 && String.valueOf(args[1]).matches(String.valueOf(args[0]));
            } catch (Exception e) {
                return false;
            }
        };
    }

    private static Function mustRegexMatch() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustRegexMatch: insufficient arguments");
            try {
                return String.valueOf(args[1]).matches(String.valueOf(args[0]));
            } catch (Exception e) {
                throw new RuntimeException("mustRegexMatch: " + e.getMessage(), e);
            }
        };
    }

    private static Function regexFind() {
        return args -> {
            if (args.length < 2) return "";
            try {
                Matcher m = Pattern.compile(String.valueOf(args[0])).matcher(String.valueOf(args[1]));
                return m.find() ? m.group() : "";
            } catch (Exception e) {
                return "";
            }
        };
    }

    private static Function mustRegexFind() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustRegexFind: insufficient arguments");
            try {
                Matcher m = Pattern.compile(String.valueOf(args[0])).matcher(String.valueOf(args[1]));
                if (!m.find()) throw new RuntimeException("mustRegexFind: no match found");
                return m.group();
            } catch (Exception e) {
                throw new RuntimeException("mustRegexFind: " + e.getMessage(), e);
            }
        };
    }

    private static Function regexFindAll() {
        return args -> {
            if (args.length < 3) return Collections.emptyList();
            try {
                String regex = String.valueOf(args[0]);
                String text = String.valueOf(args[1]);
                int n = ((Number) args[2]).intValue();

                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(text);
                List<String> results = new ArrayList<>();

                while (matcher.find() && (n < 0 || results.size() < n)) {
                    results.add(matcher.group());
                }
                return results;
            } catch (Exception e) {
                return Collections.emptyList();
            }
        };
    }

    private static Function regexReplaceAll() {
        return args -> {
            if (args.length < 3) return "";
            try {
                // Sprig signature: regexReplaceAll pattern text replacement
                // So args are: [0]=pattern, [1]=text, [2]=replacement
                String pattern = String.valueOf(args[0]);
                String text = String.valueOf(args[1]);
                String replacement = String.valueOf(args[2]);

                return text.replaceAll(pattern, replacement);
            } catch (Exception e) {
                return String.valueOf(args[1]);  // Return original text on error
            }
        };
    }

    private static Function mustRegexReplaceAll() {
        return args -> {
            if (args.length < 3) throw new RuntimeException("mustRegexReplaceAll: insufficient arguments");
            try {
                // Sprig signature: mustRegexReplaceAll pattern text replacement
                String pattern = String.valueOf(args[0]);
                String text = String.valueOf(args[1]);
                String replacement = String.valueOf(args[2]);
                return text.replaceAll(pattern, replacement);
            } catch (Exception e) {
                throw new RuntimeException("mustRegexReplaceAll: " + e.getMessage(), e);
            }
        };
    }

    private static Function regexReplaceAllLiteral() {
        return args -> {
            if (args.length < 3) return "";
            try {
                // Sprig signature: regexReplaceAllLiteral pattern replacement text
                String pattern = String.valueOf(args[0]);
                String replacement = Matcher.quoteReplacement(String.valueOf(args[1]));
                String text = String.valueOf(args[2]);
                return text.replaceAll(pattern, replacement);
            } catch (Exception e) {
                return String.valueOf(args[2]);  // Return original text on error
            }
        };
    }

    private static Function mustRegexReplaceAllLiteral() {
        return args -> {
            if (args.length < 3) throw new RuntimeException("mustRegexReplaceAllLiteral: insufficient arguments");
            try {
                // Sprig signature: mustRegexReplaceAllLiteral pattern replacement text
                String pattern = String.valueOf(args[0]);
                String replacement = Matcher.quoteReplacement(String.valueOf(args[1]));
                String text = String.valueOf(args[2]);
                return text.replaceAll(pattern, replacement);
            } catch (Exception e) {
                throw new RuntimeException("mustRegexReplaceAllLiteral: " + e.getMessage(), e);
            }
        };
    }

    private static Function regexSplit() {
        return args -> {
            if (args.length < 3) return Collections.emptyList();
            try {
                String regex = String.valueOf(args[0]);
                String text = String.valueOf(args[1]);
                int n = ((Number) args[2]).intValue();
                String[] parts = text.split(regex, n);
                return Arrays.asList(parts);
            } catch (Exception e) {
                return Collections.singletonList(String.valueOf(args[1]));
            }
        };
    }

    private static Function mustRegexSplit() {
        return args -> {
            if (args.length < 3) throw new RuntimeException("mustRegexSplit: insufficient arguments");
            try {
                String regex = String.valueOf(args[0]);
                String text = String.valueOf(args[1]);
                int n = ((Number) args[2]).intValue();
                String[] parts = text.split(regex, n);
                return Arrays.asList(parts);
            } catch (Exception e) {
                throw new RuntimeException("mustRegexSplit: " + e.getMessage(), e);
            }
        };
    }
}
