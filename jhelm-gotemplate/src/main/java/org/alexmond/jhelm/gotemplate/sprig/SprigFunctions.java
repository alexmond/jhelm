package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.Function;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SprigFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // String functions
        functions.put("trim", args -> args.length == 0 ? "" : String.valueOf(args[0]).trim());
        functions.put("trunc", args -> {
            if (args.length < 2) return "";
            int length = ((Number) args[0]).intValue();
            String text = String.valueOf(args[1]);
            if (text.length() <= length) return text;
            return text.substring(0, length);
        });
        functions.put("trimAll", args -> {
            if (args.length < 2) return "";
            String cutset = String.valueOf(args[0]);
            String s = String.valueOf(args[1]);
            return s.replaceAll("^[" + Pattern.quote(cutset) + "]+|[" + Pattern.quote(cutset) + "]+$", "");
        });
        functions.put("trimSuffix", args -> {
            if (args.length < 2) return "";
            String suffix = String.valueOf(args[0]);
            String text = String.valueOf(args[1]);
            return text.endsWith(suffix) ? text.substring(0, text.length() - suffix.length()) : text;
        });
        functions.put("trimPrefix", args -> {
            if (args.length < 2) return "";
            String prefix = String.valueOf(args[0]);
            String text = String.valueOf(args[1]);
            return text.startsWith(prefix) ? text.substring(prefix.length()) : text;
        });
        functions.put("upper", args -> args.length == 0 ? "" : String.valueOf(args[0]).toUpperCase());
        functions.put("lower", args -> args.length == 0 ? "" : String.valueOf(args[0]).toLowerCase());
        functions.put("split", args -> {
            if (args.length < 2) return new String[0];
            return String.valueOf(args[1]).split(Pattern.quote(String.valueOf(args[0])));
        });
        functions.put("replace", args -> {
            if (args.length < 3) return "";
            return String.valueOf(args[2]).replace(String.valueOf(args[0]), String.valueOf(args[1]));
        });
        functions.put("regexMatch", args -> args.length >= 2 && String.valueOf(args[1]).matches(String.valueOf(args[0])));
        functions.put("regexFind", args -> {
            if (args.length < 2) return "";
            Matcher m = Pattern.compile(String.valueOf(args[0])).matcher(String.valueOf(args[1]));
            return m.find() ? m.group() : "";
        });
        functions.put("regexReplaceAll", args -> {
            if (args.length < 3) return "";
            return String.valueOf(args[2]).replaceAll(String.valueOf(args[0]), String.valueOf(args[1]));
        });
        functions.put("regexReplaceAllLiteral", args -> {
            if (args.length < 3) return "";
            String pattern = String.valueOf(args[0]);
            String replacement = Matcher.quoteReplacement(String.valueOf(args[1]));
            String text = String.valueOf(args[2]);
            return text.replaceAll(pattern, replacement);
        });
        functions.put("cat", args -> Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(" ")));
        functions.put("indent", args -> {
            if (args.length < 2) return "";
            int spaces = ((Number) args[0]).intValue();
            return String.valueOf(args[1]).replace("\n", "\n" + " ".repeat(spaces));
        });
        functions.put("nindent", args -> {
            if (args.length < 2) return "";
            int spaces = ((Number) args[0]).intValue();
            return "\n" + " ".repeat(spaces) + String.valueOf(args[1]).replace("\n", "\n" + " ".repeat(spaces));
        });
        functions.put("quote", args -> args.length == 0 ? "\"\"" : "\"" + args[0] + "\"");
        functions.put("squote", args -> args.length == 0 ? "''" : "'" + args[0] + "'");

        // Collection functions
        functions.put("list", args -> new ArrayList<>(Arrays.asList(args)));
        functions.put("tuple", args -> Arrays.asList(args)); // Tuple is immutable list in Sprig
        functions.put("dict", args -> {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 < args.length) map.put(String.valueOf(args[i]), args[i + 1]);
            }
            return map;
        });
        functions.put("has", inFunc());
        functions.put("hasKey", args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return false;
            return ((Map<?, ?>) args[0]).containsKey(String.valueOf(args[1]));
        });
        functions.put("in", inFunc());
        functions.put("contains", args -> args.length >= 2 && String.valueOf(args[1]).contains(String.valueOf(args[0])));
        functions.put("keys", args -> args.length > 0 && args[0] instanceof Map ? new ArrayList<>(((Map<?, ?>) args[0]).keySet()) : Collections.emptyList());
        functions.put("values", args -> args.length > 0 && args[0] instanceof Map ? new ArrayList<>(((Map<?, ?>) args[0]).values()) : Collections.emptyList());
        functions.put("dig", args -> {
            // dig traverses a dict using keys: dig "key1" "key2" "default" dict
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
        });
        functions.put("pick", args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return args.length > 0 ? args[0] : Map.of();
            Map<String, Object> src = (Map<String, Object>) args[0];
            Map<String, Object> dest = new LinkedHashMap<>();
            for (int i = 1; i < args.length; i++) {
                String key = String.valueOf(args[i]);
                if (src.containsKey(key)) dest.put(key, src.get(key));
            }
            return dest;
        });
        functions.put("omit", args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return args.length > 0 ? args[0] : Map.of();
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) args[0]);
            for (int i = 1; i < args.length; i++) map.remove(String.valueOf(args[i]));
            return map;
        });
        functions.put("merge", mergeFunc());
        functions.put("mergeOverwrite", mergeFunc()); // Simple fallback
        functions.put("mustMerge", mergeFunc()); // Must version, same as merge
        functions.put("mustMergeOverwrite", mergeFunc()); // Must version, same as mergeOverwrite
        functions.put("deepCopy", args -> {
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
        });
        functions.put("append", args -> {
            if (args.length < 2 || !(args[0] instanceof Collection))
                return args.length > 0 ? args[0] : Collections.emptyList();
            List<Object> list = new ArrayList<>((Collection<?>) args[0]);
            for (int i = 1; i < args.length; i++) list.add(args[i]);
            return list;
        });
        functions.put("prepend", args -> {
            if (args.length < 2 || !(args[0] instanceof Collection))
                return args.length > 0 ? args[0] : Collections.emptyList();
            List<Object> list = new ArrayList<>();
            for (int i = 1; i < args.length; i++) list.add(args[i]);
            list.addAll((Collection<?>) args[0]);
            return list;
        });
        functions.put("concat", args -> {
            List<Object> res = new ArrayList<>();
            for (Object arg : args) {
                if (arg instanceof Collection) res.addAll((Collection<?>) arg);
                else if (arg != null) res.add(arg);
            }
            return res;
        });
        functions.put("until", args -> {
            if (args.length == 0) return Collections.emptyList();
            int n = ((Number) args[0]).intValue();
            List<Integer> res = new ArrayList<>();
            for (int i = 0; i < n; i++) res.add(i);
            return res;
        });

        // Logic & Control
        functions.put("default", args -> {
            if (args.length < 2) return args.length == 1 ? args[0] : null;
            Object defaultVal = args[0];
            Object actualVal = args[1];
            return isTruthy(actualVal) ? actualVal : defaultVal;
        });
        functions.put("empty", args -> args.length == 0 || !isTruthy(args[0]));
        functions.put("coalesce", args -> {
            for (Object arg : args) if (isTruthy(arg)) return arg;
            return null;
        });
        functions.put("ternary", args -> {
            if (args.length < 3) return "";
            return isTruthy(args[2]) ? args[0] : args[1];
        });
        functions.put("fail", args -> {
            throw new RuntimeException(args.length > 0 ? String.valueOf(args[0]) : "fail");
        });

        // Math & Conversions
        functions.put("int", args -> args.length == 0 || args[0] == null ? 0 : ((Number) args[0]).intValue());
        functions.put("int64", args -> args.length == 0 || args[0] == null ? 0L : ((Number) args[0]).longValue());
        functions.put("toString", args -> args.length == 0 ? "" : String.valueOf(args[0]));
        functions.put("float64", args -> args.length == 0 || args[0] == null ? 0.0 : ((Number) args[0]).doubleValue());
        functions.put("add", args -> {
            if (args.length < 2) return 0;
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
        });
        functions.put("sub", args -> {
            if (args.length < 2) return 0;
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
        });
        functions.put("mul", args -> {
            if (args.length < 2) return 0;
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
        });
        functions.put("div", args -> {
            if (args.length < 2) return 0;
            double dividend = ((Number) args[0]).doubleValue();
            double divisor = ((Number) args[1]).doubleValue();
            if (divisor == 0) return 0; // Avoid division by zero
            double result = dividend / divisor;
            if (result == Math.floor(result)) {
                return (long) result;
            }
            return result;
        });
        functions.put("add1", args -> {
            if (args.length == 0 || args[0] == null) return 1;
            if (args[0] instanceof Number) {
                Number num = (Number) args[0];
                if (args[0] instanceof Double || args[0] instanceof Float) {
                    return num.doubleValue() + 1;
                } else {
                    return num.longValue() + 1;
                }
            }
            return 1;
        });

        // Encoding
        functions.put("b64enc", args -> args.length == 0 ? "" : Base64.getEncoder().encodeToString(String.valueOf(args[0]).getBytes()));
        functions.put("b64dec", args -> args.length == 0 ? "" : new String(Base64.getDecoder().decode(String.valueOf(args[0]))));
        functions.put("sha256sum", args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(String.valueOf(args[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) {
                    String h = Integer.toHexString(0xff & b);
                    if (h.length() == 1) hex.append('0');
                    hex.append(h);
                }
                return hex.toString();
            } catch (Exception e) {
                return "";
            }
        });

        // Reflection
        functions.put("kindOf", args -> {
            if (args.length == 0 || args[0] == null) return "invalid";
            Class<?> c = args[0].getClass();
            if (c == String.class) return "string";
            if (Number.class.isAssignableFrom(c)) return "number";
            if (Boolean.class.isAssignableFrom(c)) return "bool";
            if (Map.class.isAssignableFrom(c)) return "map";
            if (Collection.class.isAssignableFrom(c) || c.isArray()) return "slice";
            return "struct";
        });
        functions.put("typeOf", args -> args.length == 0 || args[0] == null ? "nil" : args[0].getClass().getName());
        functions.put("kindIs", args -> {
            if (args.length < 2) return false;
            String kind = String.valueOf(args[0]);
            Object val = args[1];
            if (val == null) return "invalid".equals(kind);
            Class<?> c = val.getClass();
            return switch (kind) {
                case "string" -> c == String.class;
                case "number", "int", "float", "float64" -> Number.class.isAssignableFrom(c);
                case "bool" -> Boolean.class.isAssignableFrom(c);
                case "map" -> Map.class.isAssignableFrom(c);
                case "slice", "list" -> Collection.class.isAssignableFrom(c) || c.isArray();
                default -> false;
            };
        });
        functions.put("typeIs", args -> {
            if (args.length < 2) return false;
            String type = String.valueOf(args[0]).toLowerCase();
            Object val = args[1];
            if (val == null) return "nil".equals(type);
            String className = val.getClass().getSimpleName().toLowerCase();
            String fullName = val.getClass().getName().toLowerCase();
            return className.equals(type) || fullName.contains(type);
        });

        // Additional string functions
        functions.put("trunc", args -> {
            if (args.length < 2) return "";
            int len = ((Number) args[0]).intValue();
            String s = String.valueOf(args[1]);
            return s.length() > len ? s.substring(0, len) : s;
        });
        functions.put("substr", args -> {
            if (args.length < 3) return "";
            int start = ((Number) args[0]).intValue();
            int end = ((Number) args[1]).intValue();
            String s = String.valueOf(args[2]);
            if (start < 0) start = 0;
            if (end > s.length()) end = s.length();
            if (start > end) return "";
            return s.substring(start, end);
        });
        functions.put("shuffle", args -> {
            if (args.length == 0 || args[0] == null) return "";
            String s = String.valueOf(args[0]);
            List<Character> chars = new ArrayList<>();
            for (char c : s.toCharArray()) chars.add(c);
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            return sb.toString();
        });
        functions.put("join", args -> {
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
        });
        functions.put("splitList", args -> {
            if (args.length < 2) return Collections.emptyList();
            String sep = String.valueOf(args[0]);
            String s = String.valueOf(args[1]);
            return Arrays.asList(s.split(Pattern.quote(sep)));
        });

        // Additional collection functions
        functions.put("hasKey", args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return false;
            return ((Map<?, ?>) args[0]).containsKey(String.valueOf(args[1]));
        });
        functions.put("get", args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return null;
            return ((Map<?, ?>) args[0]).get(String.valueOf(args[1]));
        });
        functions.put("without", args -> {
            if (args.length < 2 || !(args[0] instanceof Collection))
                return args.length > 0 ? args[0] : Collections.emptyList();
            List<Object> result = new ArrayList<>((Collection<?>) args[0]);
            for (int i = 1; i < args.length; i++) {
                result.remove(args[i]);
            }
            return result;
        });
        functions.put("compact", args -> {
            if (args.length == 0 || !(args[0] instanceof Collection)) return Collections.emptyList();
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<?>) args[0]) {
                if (item != null && !item.equals("") && !item.equals(false)) {
                    result.add(item);
                }
            }
            return result;
        });
        functions.put("fromYamlArray", args -> {
            // Simplified implementation - returns empty list for now
            // Full implementation would require a YAML parser
            return Collections.emptyList();
        });

        // Additional missing Sprig functions
        functions.put("required", args -> {
            if (args.length < 2) throw new RuntimeException("required: insufficient arguments");
            String message = String.valueOf(args[0]);
            Object value = args[1];
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                throw new RuntimeException(message);
            }
            return value;
        });
        functions.put("toJson", args -> {
            // Simplified JSON serialization - this would need proper JSON library in production
            if (args.length == 0 || args[0] == null) return "null";
            Object obj = args[0];
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
            if (obj instanceof Map) return String.valueOf(obj); // Simplified
            if (obj instanceof Collection) return String.valueOf(obj); // Simplified
            return "\"" + obj + "\"";
        });
        functions.put("mustRegexReplaceAllLiteral", args -> {
            if (args.length < 3) return "";
            String pattern = String.valueOf(args[0]);
            String replacement = String.valueOf(args[1]);
            String text = String.valueOf(args[2]);
            return text.replaceAll(Pattern.quote(pattern), replacement);
        });
        functions.put("htpasswd", args -> {
            // Simplified implementation - in production this would generate proper htpasswd hash
            if (args.length < 2) return "";
            String username = String.valueOf(args[0]);
            String password = String.valueOf(args[1]);
            // Return a basic bcrypt-like format (not real htpasswd, just placeholder)
            return username + ":$2y$" + password.hashCode();
        });

        // Collection manipulation functions
        functions.put("first", args -> {
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
        });
        functions.put("uniq", args -> {
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
        });
        functions.put("mustUniq", args -> {
            // mustUniq is same as uniq but throws on error (we don't throw here)
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
        });
        functions.put("sortAlpha", args -> {
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
        });
        functions.put("reverse", args -> {
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
        });

        // Dictionary/Map manipulation
        functions.put("set", args -> {
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
            // If not a map, create a new one
            Map<String, Object> newMap = new HashMap<>();
            newMap.put(key, value);
            return newMap;
        });
        functions.put("unset", args -> {
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
        });

        // Random string generation functions
        functions.put("randAlphaNum", args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        });
        functions.put("randAlpha", args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        });
        functions.put("randNumeric", args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            String chars = "0123456789";
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        });
        functions.put("randAscii", args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                // ASCII printable characters: 33-126
                sb.append((char) (random.nextInt(94) + 33));
            }
            return sb.toString();
        });

        // Certificate generation (simplified stub)
        functions.put("genCA", args -> {
            // Simplified implementation - returns a placeholder certificate structure
            // In production, this would use proper cryptography libraries
            Map<String, Object> ca = new HashMap<>();
            ca.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...PLACEHOLDER...==\n-----END CERTIFICATE-----");
            ca.put("Key", "-----BEGIN RSA PRIVATE KEY-----\nMIIE...PLACEHOLDER...==\n-----END RSA PRIVATE KEY-----");
            return ca;
        });
        functions.put("genSignedCert", args -> {
            // Simplified implementation - returns a placeholder certificate structure
            // In production, this would generate a proper signed certificate
            // Args: cn, ips, alternateIPs, daysValid, ca
            Map<String, Object> cert = new HashMap<>();
            cert.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...SIGNED_PLACEHOLDER...==\n-----END CERTIFICATE-----");
            cert.put("Key", "-----BEGIN RSA PRIVATE KEY-----\nMIIE...SIGNED_PLACEHOLDER...==\n-----END RSA PRIVATE KEY-----");
            return cert;
        });

        // Semantic versioning function
        functions.put("semver", args -> {
            // Simplified implementation - parses semantic version string
            // Returns a map with Major, Minor, Patch fields
            if (args.length == 0) return null;
            String version = String.valueOf(args[0]);
            // Remove leading 'v' if present
            if (version.startsWith("v") || version.startsWith("V")) {
                version = version.substring(1);
            }
            Map<String, Object> semver = new HashMap<>();
            String[] parts = version.split("\\.");
            semver.put("Major", parts.length > 0 ? parseLong(parts[0]) : 0L);
            semver.put("Minor", parts.length > 1 ? parseLong(parts[1]) : 0L);
            semver.put("Patch", parts.length > 2 ? parseLong(parts[2].split("-")[0]) : 0L);
            return semver;
        });

        // Date/Time functions
        functions.put("now", args -> Date.from(Instant.now()));
        functions.put("date", args -> {
            if (args.length < 2) return "";
            String layout = String.valueOf(args[0]);
            Object dateObj = args[1];
            Date date;
            if (dateObj instanceof Date) {
                date = (Date) dateObj;
            } else if (dateObj instanceof Number) {
                date = new Date(((Number) dateObj).longValue());
            } else {
                return "";
            }
            // Convert Go date format to Java SimpleDateFormat
            // Go uses "2006-01-02 15:04:05" while Java uses "yyyy-MM-dd HH:mm:ss"
            String javaLayout = layout
                    .replace("2006", "yyyy")
                    .replace("01", "MM")
                    .replace("02", "dd")
                    .replace("15", "HH")
                    .replace("04", "mm")
                    .replace("05", "ss");
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(javaLayout);
                return sdf.format(date);
            } catch (Exception e) {
                return "";
            }
        });
        functions.put("dateInZone", args -> {
            if (args.length < 3) return "";
            String layout = String.valueOf(args[0]);
            Object dateObj = args[1];
            // String timezone = String.valueOf(args[2]); // Ignored for now
            Date date;
            if (dateObj instanceof Date) {
                date = (Date) dateObj;
            } else if (dateObj instanceof Number) {
                date = new Date(((Number) dateObj).longValue());
            } else {
                return "";
            }
            String javaLayout = layout
                    .replace("2006", "yyyy")
                    .replace("01", "MM")
                    .replace("02", "dd")
                    .replace("15", "HH")
                    .replace("04", "mm")
                    .replace("05", "ss");
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(javaLayout);
                return sdf.format(date);
            } catch (Exception e) {
                return "";
            }
        });

        // JSON functions
        functions.put("toJson", args -> {
            if (args.length == 0) return "null";
            // Simple JSON serialization
            Object obj = args[0];
            if (obj == null) return "null";
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
            if (obj instanceof Map || obj instanceof Collection) {
                // For complex objects, just return string representation for now
                return String.valueOf(obj);
            }
            return "\"" + obj + "\"";
        });
        functions.put("toRawJson", args -> {
            // toRawJson is like toJson but doesn't escape HTML
            if (args.length == 0) return "null";
            Object obj = args[0];
            if (obj == null) return "null";
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
            return String.valueOf(obj);
        });
        functions.put("mustToJson", args -> {
            // mustToJson is like toJson but fails on error
            if (args.length == 0) return "null";
            Object obj = args[0];
            if (obj == null) return "null";
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
            if (obj instanceof Collection) {
                List<String> items = new ArrayList<>();
                for (Object item : (Collection<?>) obj) {
                    if (item instanceof String) items.add("\"" + item + "\"");
                    else if (item instanceof Number || item instanceof Boolean) items.add(String.valueOf(item));
                    else items.add("\"" + item + "\"");
                }
                return "[" + String.join(",", items) + "]";
            }
            return "\"" + obj + "\"";
        });
        functions.put("fromJsonArray", args -> {
            // Parse JSON array and return as List
            if (args.length == 0) return new ArrayList<>();
            String json = String.valueOf(args[0]);
            if (json == null || json.trim().isEmpty() || json.equals("null")) return new ArrayList<>();
            // Simple JSON array parsing - remove brackets and split by comma
            json = json.trim();
            if (json.startsWith("[")) json = json.substring(1);
            if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
            if (json.trim().isEmpty()) return new ArrayList<>();

            List<String> result = new ArrayList<>();
            String[] parts = json.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                // Remove quotes if present
                if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                result.add(trimmed);
            }
            return result;
        });

        // Math functions
        functions.put("floor", args -> {
            if (args.length == 0) return 0L;
            Object val = args[0];
            if (val instanceof Number) {
                return (long) Math.floor(((Number) val).doubleValue());
            }
            return 0L;
        });
        functions.put("ceil", args -> {
            if (args.length == 0) return 0L;
            Object val = args[0];
            if (val instanceof Number) {
                return (long) Math.ceil(((Number) val).doubleValue());
            }
            return 0L;
        });
        functions.put("round", args -> {
            if (args.length == 0) return 0L;
            Object val = args[0];
            if (val instanceof Number) {
                return Math.round(((Number) val).doubleValue());
            }
            return 0L;
        });

        // Additional string functions
        functions.put("hasSuffix", args -> {
            if (args.length < 2) return false;
            String suffix = String.valueOf(args[0]);
            String text = String.valueOf(args[1]);
            return text.endsWith(suffix);
        });
        functions.put("hasPrefix", args -> {
            if (args.length < 2) return false;
            String prefix = String.valueOf(args[0]);
            String text = String.valueOf(args[1]);
            return text.startsWith(prefix);
        });

        // List functions
        functions.put("first", args -> {
            if (args.length == 0) return null;
            Object val = args[0];
            if (val instanceof List && !((List<?>) val).isEmpty()) {
                return ((List<?>) val).get(0);
            }
            if (val != null && val.getClass().isArray() && java.lang.reflect.Array.getLength(val) > 0) {
                return java.lang.reflect.Array.get(val, 0);
            }
            return null;
        });
        functions.put("last", args -> {
            if (args.length == 0) return null;
            Object val = args[0];
            if (val instanceof List) {
                List<?> list = (List<?>) val;
                return list.isEmpty() ? null : list.get(list.size() - 1);
            }
            if (val != null && val.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(val);
                return len > 0 ? java.lang.reflect.Array.get(val, len - 1) : null;
            }
            return null;
        });

        // Additional math functions
        functions.put("mod", args -> {
            if (args.length < 2) return 0L;
            long a = ((Number) args[0]).longValue();
            long b = ((Number) args[1]).longValue();
            return b != 0 ? a % b : 0L;
        });
        functions.put("mulf", args -> {
            if (args.length < 2) return 0.0;
            double result = ((Number) args[0]).doubleValue();
            for (int i = 1; i < args.length; i++) {
                result *= ((Number) args[i]).doubleValue();
            }
            return result;
        });
        functions.put("addf", args -> {
            if (args.length < 2) return 0.0;
            double result = 0;
            for (Object arg : args) {
                result += ((Number) arg).doubleValue();
            }
            return result;
        });
        functions.put("divf", args -> {
            if (args.length < 2) return 0.0;
            double result = ((Number) args[0]).doubleValue();
            for (int i = 1; i < args.length; i++) {
                double divisor = ((Number) args[i]).doubleValue();
                if (divisor != 0) {
                    result /= divisor;
                }
            }
            return result;
        });

        // URL functions
        functions.put("urlParse", args -> {
            if (args.length == 0) return Map.of();
            try {
                java.net.URL url = new java.net.URL(String.valueOf(args[0]));
                Map<String, Object> result = new HashMap<>();
                result.put("scheme", url.getProtocol());
                result.put("host", url.getHost());
                result.put("port", url.getPort() > 0 ? String.valueOf(url.getPort()) : "");
                result.put("path", url.getPath());
                result.put("query", url.getQuery() != null ? url.getQuery() : "");
                return result;
            } catch (Exception e) {
                return Map.of();
            }
        });

        // Regex functions
        functions.put("regexMatch", args -> {
            if (args.length < 2) return false;
            try {
                String regex = String.valueOf(args[0]);
                String text = String.valueOf(args[1]);
                return text.matches(regex);
            } catch (Exception e) {
                return false;
            }
        });
        functions.put("mustRegexMatch", args -> {
            // Same as regexMatch but should throw on error (we'll just return false for simplicity)
            if (args.length < 2) return false;
            try {
                String regex = String.valueOf(args[0]);
                String text = String.valueOf(args[1]);
                return text.matches(regex);
            } catch (Exception e) {
                return false;
            }
        });

        return functions;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Function inFunc() {
        return args -> {
            if (args.length < 2) return false;
            Object val = args[0];
            Object container = args[1];
            if (container instanceof Collection) return ((Collection<?>) container).contains(val);
            if (container instanceof Map) return ((Map<?, ?>) container).containsKey(val);
            if (container != null && container.getClass().isArray()) {
                int len = Array.getLength(container);
                for (int i = 0; i < len; i++) if (Objects.equals(val, Array.get(container, i))) return true;
            }
            return Objects.equals(val, container);
        };
    }

    private static Function mergeFunc() {
        return args -> {
            if (args.length < 2 || !(args[0] instanceof Map)) return args.length > 0 ? args[0] : new HashMap<>();
            Map<String, Object> dest = (Map<String, Object>) args[0];
            for (int i = 1; i < args.length; i++) {
                if (args[i] instanceof Map) deepMerge(dest, (Map<String, Object>) args[i]);
            }
            return dest;
        };
    }

    private static void deepMerge(Map<String, Object> dest, Map<String, Object> src) {
        for (Map.Entry<String, Object> entry : src.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof Map && dest.get(entry.getKey()) instanceof Map) {
                deepMerge((Map<String, Object>) dest.get(entry.getKey()), (Map<String, Object>) v);
            } else {
                dest.put(entry.getKey(), v);
            }
        }
    }

    private static boolean isTruthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return !((String) o).isEmpty();
        if (o instanceof Number) return ((Number) o).doubleValue() != 0;
        if (o instanceof Collection) return !((Collection<?>) o).isEmpty();
        if (o instanceof Map) return !((Map<?, ?>) o).isEmpty();
        if (o.getClass().isArray()) return Array.getLength(o) > 0;
        return true;
    }
}
