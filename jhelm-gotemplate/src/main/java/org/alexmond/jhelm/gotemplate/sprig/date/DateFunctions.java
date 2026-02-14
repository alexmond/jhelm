package org.alexmond.jhelm.gotemplate.sprig.date;

import org.alexmond.jhelm.gotemplate.Function;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Date and time manipulation functions from Sprig library.
 * Includes date formatting, parsing, and manipulation operations.
 *
 * @see <a href="https://masterminds.github.io/sprig/date.html">Sprig Date Functions</a>
 */
public class DateFunctions {

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
        functions.put("durationRound", durationRound());

        // Unix epoch
        functions.put("unixEpoch", unixEpoch());

        return functions;
    }

    // ========== Current Time Functions ==========

    /**
     * Returns the current date/time.
     */
    private static Function now() {
        return args -> Date.from(Instant.now());
    }

    // ========== Date Formatting Functions ==========

    /**
     * Formats a date using the given layout.
     * Go date format is converted to Java SimpleDateFormat.
     */
    private static Function date() {
        return args -> {
            if (args.length < 2) return "";
            String layout = String.valueOf(args[0]);
            Object dateObj = args[1];

            Date date = convertToDate(dateObj);
            if (date == null) return "";

            String javaLayout = convertGoLayoutToJava(layout);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(javaLayout);
                return sdf.format(date);
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * Formats a date using the given layout in a specific timezone.
     */
    private static Function dateInZone() {
        return args -> {
            if (args.length < 3) return "";
            String layout = String.valueOf(args[0]);
            Object dateObj = args[1];
            String timezone = String.valueOf(args[2]);

            Date date = convertToDate(dateObj);
            if (date == null) return "";

            String javaLayout = convertGoLayoutToJava(layout);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(javaLayout);
                sdf.setTimeZone(TimeZone.getTimeZone(timezone));
                return sdf.format(date);
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * Formats a date in HTML date format (YYYY-MM-DD).
     */
    private static Function htmlDate() {
        return args -> {
            if (args.length == 0) return "";
            Date date = convertToDate(args[0]);
            if (date == null) return "";

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.format(date);
        };
    }

    /**
     * Formats a date in HTML date format (YYYY-MM-DD) in a specific timezone.
     */
    private static Function htmlDateInZone() {
        return args -> {
            if (args.length < 2) return "";
            Date date = convertToDate(args[0]);
            if (date == null) return "";

            String timezone = String.valueOf(args[1]);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone(timezone));
            return sdf.format(date);
        };
    }

    // ========== Date Parsing Functions ==========

    /**
     * Parses a date string using the given layout.
     *
     * @return Date object or null on error
     */
    private static Function toDate() {
        return args -> {
            if (args.length < 2) return null;
            String layout = String.valueOf(args[0]);
            String dateStr = String.valueOf(args[1]);

            String javaLayout = convertGoLayoutToJava(layout);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(javaLayout);
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                return null;
            }
        };
    }

    /**
     * Parses a date string using the given layout.
     * Throws an exception on error.
     *
     * @return Date object
     * @throws RuntimeException if parsing fails
     */
    private static Function mustToDate() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("mustToDate: insufficient arguments");
            }
            String layout = String.valueOf(args[0]);
            String dateStr = String.valueOf(args[1]);

            String javaLayout = convertGoLayoutToJava(layout);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(javaLayout);
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                throw new RuntimeException("mustToDate: failed to parse date: " + e.getMessage(), e);
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
        return args -> {
            if (args.length < 2) return null;
            String modification = String.valueOf(args[0]);
            Date date = convertToDate(args[1]);
            if (date == null) return null;

            try {
                // Parse modification string (e.g., "+1h", "-30m", "+24h")
                long millisToAdd = parseDurationToMillis(modification);
                return new Date(date.getTime() + millisToAdd);
            } catch (Exception e) {
                return date;
            }
        };
    }

    /**
     * Rounds a duration to the nearest unit.
     * <p>
     * Simplified implementation.
     *
     * @return rounded duration
     */
    private static Function durationRound() {
        return args -> {
            if (args.length == 0) return "0s";
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
            } catch (Exception e) {
                return "0s";
            }
        };
    }

    // ========== Unix Epoch Functions ==========

    /**
     * Returns the Unix epoch timestamp (seconds since Jan 1, 1970 UTC).
     */
    private static Function unixEpoch() {
        return args -> {
            Date date;
            if (args.length == 0) {
                date = Date.from(Instant.now());
            } else {
                date = convertToDate(args[0]);
            }
            if (date == null) return 0L;
            return date.getTime() / 1000;
        };
    }

    // ========== Helper Methods ==========

    /**
     * Converts Go date format to Java SimpleDateFormat.
     * Go uses "2006-01-02 15:04:05" while Java uses "yyyy-MM-dd HH:mm:ss"
     */
    private static String convertGoLayoutToJava(String goLayout) {
        return goLayout
                .replace("2006", "yyyy")
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
        } else if (dateObj instanceof Number) {
            return new Date(((Number) dateObj).longValue());
        } else if (dateObj instanceof Instant) {
            return Date.from((Instant) dateObj);
        }
        return null;
    }

    /**
     * Parses duration string to milliseconds.
     * Supports formats like: "+1h", "-30m", "+24h30m", "1h30m45s"
     */
    private static long parseDurationToMillis(String duration) {
        duration = duration.trim();
        boolean negative = duration.startsWith("-");
        if (negative || duration.startsWith("+")) {
            duration = duration.substring(1);
        }

        long totalMillis = 0;
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < duration.length(); i++) {
            char c = duration.charAt(i);
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.length() > 0) {
                    long value = Long.parseLong(number.toString());
                    switch (c) {
                        case 'h' -> totalMillis += value * 3600000; // hours to millis
                        case 'm' -> totalMillis += value * 60000;   // minutes to millis
                        case 's' -> totalMillis += value * 1000;     // seconds to millis
                        case 'd' -> totalMillis += value * 86400000; // days to millis
                    }
                    number = new StringBuilder();
                }
            }
        }

        return negative ? -totalMillis : totalMillis;
    }
}
