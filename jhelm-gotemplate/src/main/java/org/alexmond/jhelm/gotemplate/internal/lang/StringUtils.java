package org.alexmond.jhelm.gotemplate.internal.lang;

public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Quote string with double-quote
     *
     * @param str string to be quoted
     * @return quoted string
     */
    public static String quote(String str) {
        return '"' + str + '"';
    }

    /**
     * Unquote string
     *
     * @param str string with quotes at the beginning and ending
     * @return unquoted string
     * @throws IllegalArgumentException when string is not quoted properly, such as: [<code>"hello, world</code>]
     */
    public static String unquote(String str) throws IllegalArgumentException {
        int length = str.length();
        if (length < 2) {
            return str;
        }

        char quote = str.charAt(0);
        if (quote != str.charAt(length - 1)) {
            return str;
        }

        if (quote != '"' && quote != '\'' && quote != '`') {
            return str;
        }

        String unquoted = str.substring(1, length - 1);
        if (quote == '`') {
            if (unquoted.contains("\r")) {
                unquoted = unquoted.replace("\r", "");
            }
            return unquoted;
        }

        // Unescape escape sequences for double-quoted and single-quoted strings
        return unescapeQuotedString(unquoted);
    }

    /**
     * Unescape escape sequences in a quoted string (for double quotes and single quotes).
     * Handles: \", \', \\, \n, \t, \r, \b, \f
     */
    private static String unescapeQuotedString(String str) {
        if (!str.contains("\\")) {
            return str;  // Fast path - no escapes
        }

        StringBuilder result = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                char next = str.charAt(i + 1);
                switch (next) {
                    case '"':
                        result.append('"');
                        i++;
                        break;
                    case '\'':
                        result.append('\'');
                        i++;
                        break;
                    case '\\':
                        result.append('\\');
                        i++;
                        break;
                    case 'n':
                        result.append('\n');
                        i++;
                        break;
                    case 't':
                        result.append('\t');
                        i++;
                        break;
                    case 'r':
                        result.append('\r');
                        i++;
                        break;
                    case 'b':
                        result.append('\b');
                        i++;
                        break;
                    case 'f':
                        result.append('\f');
                        i++;
                        break;
                    default:
                        // Unknown escape sequence - keep the backslash
                        result.append(c);
                        break;
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

}
