package org.alexmond.jhelm.gotemplate.sprig.collections;

import org.alexmond.jhelm.gotemplate.Function;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sprig Collection manipulation functions (lists, slices, dicts)
 * Based on: <a href="https://masterminds.github.io/sprig/lists.html">https://masterminds.github.io/sprig/lists.html</a>
 * Based on: <a href="https://masterminds.github.io/sprig/dicts.html">https://masterminds.github.io/sprig/dicts.html</a>
 */
public class CollectionFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // List functions
        functions.put("list", list());
        functions.put("tuple", tuple());
        functions.put("first", first());
        functions.put("mustFirst", mustFirst());
        functions.put("rest", rest());
        functions.put("mustRest", mustRest());
        functions.put("last", last());
        functions.put("mustLast", mustLast());
        functions.put("initial", initial());
        functions.put("mustInitial", mustInitial());
        functions.put("append", append());
        functions.put("mustAppend", mustAppend());
        functions.put("prepend", prepend());
        functions.put("mustPrepend", mustPrepend());
        functions.put("concat", concat());
        functions.put("reverse", reverse());
        functions.put("mustReverse", mustReverse());
        functions.put("uniq", uniq());
        functions.put("mustUniq", mustUniq());
        functions.put("without", without());
        functions.put("mustWithout", mustWithout());
        functions.put("has", has());
        functions.put("mustHas", mustHas());
        functions.put("slice", slice());
        functions.put("mustSlice", mustSlice());
        functions.put("until", until());
        functions.put("untilStep", untilStep());
        functions.put("seq", seq());
        functions.put("compact", compact());
        functions.put("mustCompact", mustCompact());
        functions.put("sortAlpha", sortAlpha());
        functions.put("split", split());
        functions.put("splitList", splitList());
        functions.put("splitn", splitn());
        functions.put("join", join());

        // Dict functions
        functions.put("dict", dict());
        functions.put("get", get());
        functions.put("set", set());
        functions.put("unset", unset());
        functions.put("hasKey", hasKey());
        functions.put("mustHasKey", mustHasKey());
        functions.put("pluck", pluck());
        functions.put("dig", dig());
        functions.put("merge", merge());
        functions.put("mergeOverwrite", mergeOverwrite());
        functions.put("mustMerge", mustMerge());
        functions.put("mustMergeOverwrite", mustMergeOverwrite());
        functions.put("keys", keys());
        functions.put("mustKeys", mustKeys());
        functions.put("pick", pick());
        functions.put("mustPick", mustPick());
        functions.put("omit", omit());
        functions.put("mustOmit", mustOmit());
        functions.put("values", values());
        functions.put("mustValues", mustValues());
        functions.put("deepCopy", deepCopy());
        functions.put("mustDeepCopy", mustDeepCopy());

        return functions;
    }

    // List functions
    private static Function list() {
        return args -> new ArrayList<>(Arrays.asList(args));
    }

    private static Function tuple() {
        return args -> Arrays.asList(args);
    }

    private static Function first() {
        return args -> {
            if (args.length == 0 || args[0] == null) return null;
            Object obj = args[0];
            if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                return list.isEmpty() ? null : list.get(0);
            }
            if (obj instanceof Collection) {
                Collection<?> col = (Collection<?>) obj;
                return col.isEmpty() ? null : col.iterator().next();
            }
            if (obj.getClass().isArray()) {
                return Array.getLength(obj) > 0 ? Array.get(obj, 0) : null;
            }
            if (obj instanceof String) {
                String s = (String) obj;
                return s.isEmpty() ? "" : String.valueOf(s.charAt(0));
            }
            return null;
        };
    }

    private static Function mustFirst() {
        return args -> {
            Object result = first().invoke(args);
            if (result == null) throw new RuntimeException("mustFirst: list is empty");
            return result;
        };
    }

    private static Function rest() {
        return args -> {
            if (args.length == 0 || args[0] == null) return Collections.emptyList();
            Object obj = args[0];
            if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                return list.size() <= 1 ? Collections.emptyList() : list.subList(1, list.size());
            }
            if (obj instanceof Collection) {
                List<?> list = new ArrayList<>((Collection<?>) obj);
                return list.size() <= 1 ? Collections.emptyList() : list.subList(1, list.size());
            }
            return Collections.emptyList();
        };
    }

    private static Function mustRest() {
        return args -> {
            Object result = rest().invoke(args);
            if (result == null) throw new RuntimeException("mustRest: operation failed");
            return result;
        };
    }

    private static Function last() {
        return args -> {
            if (args.length == 0 || args[0] == null) return null;
            Object obj = args[0];
            if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                return list.isEmpty() ? null : list.get(list.size() - 1);
            }
            if (obj instanceof Collection) {
                List<?> list = new ArrayList<>((Collection<?>) obj);
                return list.isEmpty() ? null : list.get(list.size() - 1);
            }
            if (obj.getClass().isArray()) {
                int len = Array.getLength(obj);
                return len > 0 ? Array.get(obj, len - 1) : null;
            }
            return null;
        };
    }

    private static Function mustLast() {
        return args -> {
            Object result = last().invoke(args);
            if (result == null) throw new RuntimeException("mustLast: list is empty");
            return result;
        };
    }

    private static Function initial() {
        return args -> {
            if (args.length == 0 || args[0] == null) return Collections.emptyList();
            Object obj = args[0];
            if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                return list.isEmpty() ? Collections.emptyList() : list.subList(0, list.size() - 1);
            }
            if (obj instanceof Collection) {
                List<?> list = new ArrayList<>((Collection<?>) obj);
                return list.isEmpty() ? Collections.emptyList() : list.subList(0, list.size() - 1);
            }
            return Collections.emptyList();
        };
    }

    private static Function mustInitial() {
        return args -> {
            Object result = initial().invoke(args);
            if (result == null) throw new RuntimeException("mustInitial: operation failed");
            return result;
        };
    }

    private static Function append() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Collection))
                return args.length > 0 ? args[0] : Collections.emptyList();
            List<Object> list = new ArrayList<>((Collection<?>) args[0]);
            list.addAll(Arrays.asList(args).subList(1, args.length));
            return list;
        };
    }

    private static Function mustAppend() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustAppend: insufficient arguments");
            return append().invoke(args);
        };
    }

    private static Function prepend() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Collection))
                return args.length > 0 ? args[0] : Collections.emptyList();
            List<Object> list = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
            list.addAll((Collection<?>) args[0]);
            return list;
        };
    }

    private static Function mustPrepend() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustPrepend: insufficient arguments");
            return prepend().invoke(args);
        };
    }

    private static Function concat() {
        return args -> {
            List<Object> res = new ArrayList<>();
            for (Object arg : args) {
                if (arg instanceof Collection) res.addAll((Collection<?>) arg);
                else if (arg != null) res.add(arg);
            }
            return res;
        };
    }

    private static Function reverse() {
        return args -> {
            if (args.length == 0 || args[0] == null) return Collections.emptyList();
            Object obj = args[0];
            List<Object> result;
            if (obj instanceof Collection) {
                result = new ArrayList<>((Collection<?>) obj);
            } else if (obj.getClass().isArray()) {
                int len = Array.getLength(obj);
                result = new ArrayList<>();
                for (int i = 0; i < len; i++) {
                    result.add(Array.get(obj, i));
                }
            } else if (obj instanceof String) {
                String s = (String) obj;
                return new StringBuilder(s).reverse().toString();
            } else {
                result = Collections.singletonList(obj);
            }
            Collections.reverse(result);
            return result;
        };
    }

    private static Function mustReverse() {
        return args -> {
            if (args.length == 0) throw new RuntimeException("mustReverse: no arguments provided");
            return reverse().invoke(args);
        };
    }

    private static Function uniq() {
        return args -> {
            if (args.length == 0 || args[0] == null) return Collections.emptyList();
            Object obj = args[0];
            if (obj instanceof Collection) {
                return new ArrayList<>(new LinkedHashSet<>((Collection<?>) obj));
            }
            if (obj.getClass().isArray()) {
                int len = Array.getLength(obj);
                Set<Object> seen = new LinkedHashSet<>();
                for (int i = 0; i < len; i++) {
                    seen.add(Array.get(obj, i));
                }
                return new ArrayList<>(seen);
            }
            return Collections.singletonList(obj);
        };
    }

    private static Function mustUniq() {
        return args -> {
            if (args.length == 0) throw new RuntimeException("mustUniq: no arguments provided");
            return uniq().invoke(args);
        };
    }

    private static Function without() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Collection))
                return args.length > 0 ? args[0] : Collections.emptyList();
            List<Object> result = new ArrayList<>((Collection<?>) args[0]);
            for (int i = 1; i < args.length; i++) {
                result.remove(args[i]);
            }
            return result;
        };
    }

    private static Function mustWithout() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustWithout: insufficient arguments");
            return without().invoke(args);
        };
    }

    private static Function has() {
        return args -> {
            if (args.length < 2) return false;
            Object needle = args[0];
            Object haystack = args[1];
            if (haystack instanceof Collection) {
                return ((Collection<?>) haystack).contains(needle);
            }
            if (haystack instanceof String) {
                return ((String) haystack).contains(String.valueOf(needle));
            }
            return false;
        };
    }

    private static Function mustHas() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustHas: insufficient arguments");
            boolean result = (boolean) has().invoke(args);
            if (!result) throw new RuntimeException("mustHas: element not found");
            return result;
        };
    }

    private static Function slice() {
        return args -> {
            if (args.length < 2) return Collections.emptyList();
            Object list = args[0];
            int from = args.length > 1 ? ((Number) args[1]).intValue() : 0;
            int to = args.length > 2 ? ((Number) args[2]).intValue() : Integer.MAX_VALUE;

            if (list instanceof List) {
                List<?> l = (List<?>) list;
                to = Math.min(to, l.size());
                from = Math.max(0, Math.min(from, l.size()));
                return l.subList(from, to);
            }
            return Collections.emptyList();
        };
    }

    private static Function mustSlice() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustSlice: insufficient arguments");
            return slice().invoke(args);
        };
    }

    private static Function until() {
        return args -> {
            if (args.length == 0) return Collections.emptyList();
            int n = ((Number) args[0]).intValue();
            List<Integer> res = new ArrayList<>();
            for (int i = 0; i < n; i++) res.add(i);
            return res;
        };
    }

    private static Function untilStep() {
        return args -> {
            if (args.length < 3) return Collections.emptyList();
            int start = ((Number) args[0]).intValue();
            int stop = ((Number) args[1]).intValue();
            int step = ((Number) args[2]).intValue();
            if (step == 0) return Collections.emptyList();

            List<Integer> res = new ArrayList<>();
            if (step > 0) {
                for (int i = start; i < stop; i += step) res.add(i);
            } else {
                for (int i = start; i > stop; i += step) res.add(i);
            }
            return res;
        };
    }

    private static Function seq() {
        return args -> {
            if (args.length == 0) return Collections.emptyList();
            int start = 1;
            int end = ((Number) args[0]).intValue();
            int step = 1;

            if (args.length >= 2) {
                start = ((Number) args[0]).intValue();
                end = ((Number) args[1]).intValue();
            }
            if (args.length >= 3) {
                step = ((Number) args[2]).intValue();
            }

            List<Integer> res = new ArrayList<>();
            if (step > 0) {
                for (int i = start; i <= end; i += step) res.add(i);
            } else {
                for (int i = start; i >= end; i += step) res.add(i);
            }
            return res;
        };
    }

    private static Function compact() {
        return args -> {
            if (args.length == 0 || !(args[0] instanceof Collection)) return Collections.emptyList();
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<?>) args[0]) {
                if (item != null && !item.equals("") && !item.equals(false)) {
                    result.add(item);
                }
            }
            return result;
        };
    }

    private static Function mustCompact() {
        return args -> {
            if (args.length == 0) throw new RuntimeException("mustCompact: no arguments provided");
            return compact().invoke(args);
        };
    }

    private static Function sortAlpha() {
        return args -> {
            if (args.length == 0 || args[0] == null) return Collections.emptyList();
            Object obj = args[0];
            List<String> result;
            if (obj instanceof Collection) {
                result = ((Collection<?>) obj).stream()
                        .map(String::valueOf)
                        .sorted()
                        .collect(Collectors.toList());
            } else if (obj.getClass().isArray()) {
                int len = Array.getLength(obj);
                result = new ArrayList<>();
                for (int i = 0; i < len; i++) {
                    result.add(String.valueOf(Array.get(obj, i)));
                }
                Collections.sort(result);
            } else {
                result = Collections.singletonList(String.valueOf(obj));
            }
            return result;
        };
    }

    private static Function split() {
        return args -> {
            if (args.length < 2) return new String[0];
            return String.valueOf(args[1]).split(java.util.regex.Pattern.quote(String.valueOf(args[0])));
        };
    }

    private static Function splitList() {
        return args -> {
            if (args.length < 2) return Collections.emptyList();
            String sep = String.valueOf(args[0]);
            String s = String.valueOf(args[1]);
            return Arrays.asList(s.split(java.util.regex.Pattern.quote(sep)));
        };
    }

    private static Function splitn() {
        return args -> {
            if (args.length < 3) return Collections.emptyList();
            String sep = String.valueOf(args[0]);
            int n = ((Number) args[1]).intValue();
            String s = String.valueOf(args[2]);
            return Arrays.asList(s.split(java.util.regex.Pattern.quote(sep), n));
        };
    }

    private static Function join() {
        return args -> {
            if (args.length < 2) return "";
            String sep = String.valueOf(args[0]);
            Object listObj = args[1];
            if (listObj instanceof Collection) {
                return ((Collection<?>) listObj).stream().map(String::valueOf).collect(Collectors.joining(sep));
            } else if (listObj != null && listObj.getClass().isArray()) {
                int len = Array.getLength(listObj);
                List<String> strs = new ArrayList<>();
                for (int i = 0; i < len; i++) strs.add(String.valueOf(Array.get(listObj, i)));
                return String.join(sep, strs);
            }
            return String.valueOf(listObj);
        };
    }

    // Dict functions
    private static Function dict() {
        return args -> {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 < args.length) map.put(String.valueOf(args[i]), args[i + 1]);
            }
            return map;
        };
    }

    private static Function get() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return null;
            return ((Map<?, ?>) args[0]).get(String.valueOf(args[1]));
        };
    }

    private static Function set() {
        return args -> {
            if (args.length < 3) return args.length > 0 ? args[0] : null;
            Object dict = args[0];
            String key = String.valueOf(args[1]);
            Object value = args[2];
            if (dict instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) dict;
                map.put(key, value);
                return map;
            }
            Map<String, Object> newMap = new HashMap<>();
            newMap.put(key, value);
            return newMap;
        };
    }

    private static Function unset() {
        return args -> {
            if (args.length < 2) return args.length > 0 ? args[0] : null;
            Object dict = args[0];
            String key = String.valueOf(args[1]);
            if (dict instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) dict;
                map.remove(key);
                return map;
            }
            return dict;
        };
    }

    private static Function hasKey() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return false;
            return ((Map<?, ?>) args[0]).containsKey(String.valueOf(args[1]));
        };
    }

    private static Function mustHasKey() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustHasKey: insufficient arguments");
            boolean result = (boolean) hasKey().invoke(args);
            if (!result) throw new RuntimeException("mustHasKey: key not found");
            return result;
        };
    }

    private static Function pluck() {
        return args -> {
            if (args.length < 2) return Collections.emptyList();
            String key = String.valueOf(args[0]);
            List<Object> result = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                if (args[i] instanceof Map) {
                    Object val = ((Map<?, ?>) args[i]).get(key);
                    if (val != null) result.add(val);
                }
            }
            return result;
        };
    }

    private static Function dig() {
        return args -> {
            if (args.length < 2) return null;
            Object defaultValue = args.length > 2 ? args[args.length - 2] : null;
            Object current = args[args.length - 1];
            for (int i = 0; i < args.length - 2; i++) {
                if (!(current instanceof Map)) return defaultValue;
                String key = String.valueOf(args[i]);
                Map<?, ?> map = (Map<?, ?>) current;
                if (!map.containsKey(key)) return defaultValue;
                current = map.get(key);
            }
            return current != null ? current : defaultValue;
        };
    }

    @SuppressWarnings("unchecked")
    private static Function merge() {
        return args -> {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object arg : args) {
                if (arg instanceof Map) {
                    result.putAll((Map<String, Object>) arg);
                }
            }
            return result;
        };
    }

    private static Function mergeOverwrite() {
        return merge(); // Same behavior in simple implementation
    }

    private static Function mustMerge() {
        return args -> {
            if (args.length == 0) throw new RuntimeException("mustMerge: no arguments provided");
            return merge().invoke(args);
        };
    }

    private static Function mustMergeOverwrite() {
        return args -> {
            if (args.length == 0) throw new RuntimeException("mustMergeOverwrite: no arguments provided");
            return mergeOverwrite().invoke(args);
        };
    }

    private static Function keys() {
        return args -> args.length > 0 && args[0] instanceof Map ?
                new ArrayList<>(((Map<?, ?>) args[0]).keySet()) : Collections.emptyList();
    }

    private static Function mustKeys() {
        return args -> {
            if (args.length == 0 || !(args[0] instanceof Map))
                throw new RuntimeException("mustKeys: argument is not a map");
            return new ArrayList<>(((Map<?, ?>) args[0]).keySet());
        };
    }

    private static Function pick() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return args.length > 0 ? args[0] : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> src = (Map<String, Object>) args[0];
            Map<String, Object> dest = new LinkedHashMap<>();
            for (int i = 1; i < args.length; i++) {
                String key = String.valueOf(args[i]);
                if (src.containsKey(key)) dest.put(key, src.get(key));
            }
            return dest;
        };
    }

    private static Function mustPick() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustPick: insufficient arguments");
            return pick().invoke(args);
        };
    }

    private static Function omit() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return args.length > 0 ? args[0] : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) args[0]);
            for (int i = 1; i < args.length; i++) map.remove(String.valueOf(args[i]));
            return map;
        };
    }

    private static Function mustOmit() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("mustOmit: insufficient arguments");
            return omit().invoke(args);
        };
    }

    private static Function values() {
        return args -> args.length > 0 && args[0] instanceof Map ?
                new ArrayList<>(((Map<?, ?>) args[0]).values()) : Collections.emptyList();
    }

    private static Function mustValues() {
        return args -> {
            if (args.length == 0 || !(args[0] instanceof Map))
                throw new RuntimeException("mustValues: argument is not a map");
            return new ArrayList<>(((Map<?, ?>) args[0]).values());
        };
    }

    @SuppressWarnings("unchecked")
    private static Function deepCopy() {
        return args -> {
            if (args.length == 0) return null;
            Object obj = args[0];
            if (obj instanceof Map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) obj).entrySet()) {
                    copy.put(entry.getKey(), entry.getValue()); // Shallow copy for now
                }
                return copy;
            } else if (obj instanceof Collection) {
                return new ArrayList<>((Collection<?>) obj);
            }
            return obj;
        };
    }

    private static Function mustDeepCopy() {
        return args -> {
            if (args.length == 0) throw new RuntimeException("mustDeepCopy: no arguments provided");
            return deepCopy().invoke(args);
        };
    }
}
