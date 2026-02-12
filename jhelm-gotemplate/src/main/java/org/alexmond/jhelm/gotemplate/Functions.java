package org.alexmond.jhelm.gotemplate;

import org.alexmond.jhelm.gotemplate.sprig.SprigFunctions;

import java.util.*;

public class Functions {

    public static final Map<String, Function> BUILTIN = new LinkedHashMap<>();

    static {
        BUILTIN.put("call", noop());
        BUILTIN.put("html", noop());
        BUILTIN.put("index", index());
        BUILTIN.put("slice", noop());
        BUILTIN.put("js", noop());
        BUILTIN.put("len", len());
        BUILTIN.put("print", print());
        BUILTIN.put("printf", printf());
        BUILTIN.put("println", println());
        BUILTIN.put("urlquery", noop());

        // Logical operations
        BUILTIN.put("and", and());
        BUILTIN.put("or", or());
        BUILTIN.put("not", not());

        // Comparisons
        BUILTIN.put("eq", eq());
        BUILTIN.put("ge", ge());
        BUILTIN.put("gt", gt());
        BUILTIN.put("le", le());
        BUILTIN.put("lt", lt());
        BUILTIN.put("ne", ne());

        // Load Sprig functions
        BUILTIN.putAll(SprigFunctions.getFunctions());
    }

    private static Function index() {
        return args -> {
            if (args.length < 2) return null;
            Object container = args[0];
            Object key = args[1];
            if (container instanceof Map) return ((Map<?, ?>) container).get(key);
            if (container instanceof List) return ((List<?>) container).get(((Number) key).intValue());
            if (container != null && container.getClass().isArray())
                return java.lang.reflect.Array.get(container, ((Number) key).intValue());
            return null;
        };
    }

    private static Function len() {
        return args -> {
            if (args.length == 0 || args[0] == null) return 0;
            Object o = args[0];
            if (o instanceof Collection) return ((Collection<?>) o).size();
            if (o instanceof Map) return ((Map<?, ?>) o).size();
            if (o instanceof String) return ((String) o).length();
            if (o.getClass().isArray()) return java.lang.reflect.Array.getLength(o);
            return 0;
        };
    }

    private static Function and() {
        return args -> {
            for (Object arg : args) if (!isTrue(arg)) return arg;
            return args.length > 0 ? args[args.length - 1] : false;
        };
    }

    private static Function or() {
        return args -> {
            for (Object arg : args) if (isTrue(arg)) return arg;
            return args.length > 0 ? args[args.length - 1] : false;
        };
    }

    private static Function eq() {
        return args -> {
            if (args.length < 2) return false;
            Object first = args[0];
            for (int i = 1; i < args.length; i++) {
                if (!Objects.equals(first, args[i])) return false;
            }
            return true;
        };
    }

    private static Function ne() {
        return args -> args.length >= 2 && !Objects.equals(args[0], args[1]);
    }

    private static Function lt() {
        return args -> {
            if (args.length < 2) return false;
            if (!(args[0] instanceof Comparable) || args[1] == null) return false;
            try {
                @SuppressWarnings("unchecked")
                Comparable<Object> comparable = (Comparable<Object>) args[0];
                return comparable.compareTo(args[1]) < 0;
            } catch (ClassCastException e) {
                return false;
            }
        };
    }

    private static Function le() {
        return args -> {
            if (args.length < 2) return false;
            if (!(args[0] instanceof Comparable) || args[1] == null) return false;
            try {
                @SuppressWarnings("unchecked")
                Comparable<Object> comparable = (Comparable<Object>) args[0];
                return comparable.compareTo(args[1]) <= 0;
            } catch (ClassCastException e) {
                return false;
            }
        };
    }

    private static Function gt() {
        return args -> {
            if (args.length < 2) return false;
            if (!(args[0] instanceof Comparable) || args[1] == null) return false;
            try {
                @SuppressWarnings("unchecked")
                Comparable<Object> comparable = (Comparable<Object>) args[0];
                return comparable.compareTo(args[1]) > 0;
            } catch (ClassCastException e) {
                return false;
            }
        };
    }

    private static Function ge() {
        return args -> {
            if (args.length < 2) return false;
            if (!(args[0] instanceof Comparable) || args[1] == null) return false;
            try {
                @SuppressWarnings("unchecked")
                Comparable<Object> comparable = (Comparable<Object>) args[0];
                return comparable.compareTo(args[1]) >= 0;
            } catch (ClassCastException e) {
                return false;
            }
        };
    }

    private static Function noop() {
        return args -> null;
    }

    private static Function print() {
        return args -> Arrays.stream(args).map(String::valueOf).collect(java.util.stream.Collectors.joining(" "));
    }

    private static Function printf() {
        return args -> {
            if (args.length == 0) return "";
            String format = String.valueOf(args[0]);

            // Translate Go format verbs to Java equivalents
            // %v -> %s (default format)
            // %#v -> %s (Go-syntax representation, simplified to string)
            // %T -> %s (type, simplified to string representation)
            // %t -> %b (boolean)
            // %b -> %s (binary, not directly supported, use string)
            // %c -> %c (character - keep as is)
            // %d, %o, %x, %X -> keep as is (integer formats)
            // %e, %E, %f, %F, %g, %G -> keep as is (float formats)
            // %s -> %s (string - keep as is)
            // %q -> %s (quoted string, simplified)
            // %p -> %s (pointer, simplified)
            format = format.replaceAll("%#?v", "%s");  // %v and %#v -> %s
            format = format.replaceAll("%T", "%s");     // %T -> %s
            format = format.replaceAll("%q", "%s");     // %q -> %s
            format = format.replaceAll("%p", "%s");     // %p -> %s

            Object[] realArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, realArgs, 0, args.length - 1);
            return String.format(format, realArgs);
        };
    }

    private static Function println() {
        return args -> Arrays.stream(args).map(String::valueOf).collect(java.util.stream.Collectors.joining(" ")) + "\n";
    }

    private static Function not() {
        return args -> args.length > 0 && !isTrue(args[0]);
    }

    public static boolean isTrue(Object arg) {
        if (arg == null) return false;
        if (arg instanceof Boolean) return (Boolean) arg;
        if (arg instanceof String) return !((String) arg).isEmpty();
        if (arg instanceof Number) return ((Number) arg).doubleValue() != 0;
        if (arg instanceof Collection) return !((Collection<?>) arg).isEmpty();
        if (arg instanceof Map) return !((Map<?, ?>) arg).isEmpty();
        if (arg.getClass().isArray()) return java.lang.reflect.Array.getLength(arg) > 0;
        return true;
    }
}
