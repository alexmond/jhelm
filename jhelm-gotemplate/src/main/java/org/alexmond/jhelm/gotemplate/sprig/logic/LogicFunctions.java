package org.alexmond.jhelm.gotemplate.sprig.logic;

import org.alexmond.jhelm.gotemplate.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Sprig logic and control flow functions
 * Based on: https://masterminds.github.io/sprig/defaults.html
 */
public class LogicFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        functions.put("default", defaultFunc());
        functions.put("empty", empty());
        functions.put("coalesce", coalesce());
        functions.put("ternary", ternary());
        functions.put("fail", fail());

        return functions;
    }

    private static Function defaultFunc() {
        return args -> {
            if (args.length < 2) return args.length == 1 ? args[0] : null;
            Object defaultVal = args[0];
            Object actualVal = args[1];
            return isTruthy(actualVal) ? actualVal : defaultVal;
        };
    }

    private static Function empty() {
        return args -> args.length == 0 || !isTruthy(args[0]);
    }

    private static Function coalesce() {
        return args -> {
            for (Object arg : args) {
                if (isTruthy(arg)) return arg;
            }
            return null;
        };
    }

    private static Function ternary() {
        return args -> {
            if (args.length < 3) return "";
            return isTruthy(args[2]) ? args[0] : args[1];
        };
    }

    private static Function fail() {
        return args -> {
            throw new RuntimeException(args.length > 0 ? String.valueOf(args[0]) : "fail");
        };
    }

    private static boolean isTruthy(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean b -> b;
            case String s -> !s.isEmpty();
            case Number number -> number.doubleValue() != 0;
            case java.util.Collection collection -> !collection.isEmpty();
            case Map map -> !map.isEmpty();
            default -> true;
        };
    }
}
