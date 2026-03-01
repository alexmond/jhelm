package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Sprig List/Slice manipulation functions. Based on:
 * <a href="https://masterminds.github.io/sprig/lists.html">Sprig Lists</a>
 */
public final class ListFunctions {

	private ListFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();
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
		functions.put("chunk", chunk());
		functions.put("mustChunk", mustChunk());

		// Aliases
		functions.put("push", append());
		functions.put("mustPush", mustAppend());
		return functions;
	}

	private static Function list() {
		return (args) -> new ArrayList<>(Arrays.asList(args));
	}

	private static Function tuple() {
		return Arrays::asList;
	}

	private static Function first() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return null;
			}
			Object obj = args[0];
			if (obj instanceof List) {
				List<?> list = (List<?>) obj;
				return (list.isEmpty()) ? null : list.get(0);
			}
			if (obj instanceof Collection) {
				Collection<?> col = (Collection<?>) obj;
				return (col.isEmpty()) ? null : col.iterator().next();
			}
			if (obj.getClass().isArray()) {
				return (Array.getLength(obj) > 0) ? Array.get(obj, 0) : null;
			}
			if (obj instanceof String) {
				String s = (String) obj;
				return (s.isEmpty()) ? "" : String.valueOf(s.charAt(0));
			}
			return null;
		};
	}

	private static Function mustFirst() {
		return (args) -> {
			Object result = first().invoke(args);
			if (result == null) {
				throw new RuntimeException("mustFirst: list is empty");
			}
			return result;
		};
	}

	private static Function rest() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Collections.emptyList();
			}
			Object obj = args[0];
			if (obj instanceof List) {
				List<?> list = (List<?>) obj;
				return (list.size() <= 1) ? Collections.emptyList() : list.subList(1, list.size());
			}
			if (obj instanceof Collection) {
				List<?> list = new ArrayList<>((Collection<?>) obj);
				return (list.size() <= 1) ? Collections.emptyList() : list.subList(1, list.size());
			}
			return Collections.emptyList();
		};
	}

	private static Function mustRest() {
		return (args) -> {
			Object result = rest().invoke(args);
			if (result == null) {
				throw new RuntimeException("mustRest: operation failed");
			}
			return result;
		};
	}

	private static Function last() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return null;
			}
			Object obj = args[0];
			if (obj instanceof List) {
				List<?> list = (List<?>) obj;
				return (list.isEmpty()) ? null : list.get(list.size() - 1);
			}
			if (obj instanceof Collection) {
				List<?> list = new ArrayList<>((Collection<?>) obj);
				return (list.isEmpty()) ? null : list.get(list.size() - 1);
			}
			if (obj.getClass().isArray()) {
				int len = Array.getLength(obj);
				return (len > 0) ? Array.get(obj, len - 1) : null;
			}
			return null;
		};
	}

	private static Function mustLast() {
		return (args) -> {
			Object result = last().invoke(args);
			if (result == null) {
				throw new RuntimeException("mustLast: list is empty");
			}
			return result;
		};
	}

	private static Function initial() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Collections.emptyList();
			}
			Object obj = args[0];
			if (obj instanceof List) {
				List<?> list = (List<?>) obj;
				return (list.isEmpty()) ? Collections.emptyList() : list.subList(0, list.size() - 1);
			}
			if (obj instanceof Collection) {
				List<?> list = new ArrayList<>((Collection<?>) obj);
				return (list.isEmpty()) ? Collections.emptyList() : list.subList(0, list.size() - 1);
			}
			return Collections.emptyList();
		};
	}

	private static Function mustInitial() {
		return (args) -> {
			Object result = initial().invoke(args);
			if (result == null) {
				throw new RuntimeException("mustInitial: operation failed");
			}
			return result;
		};
	}

	private static Function append() {
		return (args) -> {
			if (args.length < 2 || !(args[0] instanceof Collection)) {
				return (args.length > 0) ? args[0] : Collections.emptyList();
			}
			List<Object> list = new ArrayList<>((Collection<?>) args[0]);
			list.addAll(Arrays.asList(args).subList(1, args.length));
			return list;
		};
	}

	private static Function mustAppend() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustAppend: insufficient arguments");
			}
			return append().invoke(args);
		};
	}

	private static Function prepend() {
		return (args) -> {
			if (args.length < 2 || !(args[0] instanceof Collection)) {
				return (args.length > 0) ? args[0] : Collections.emptyList();
			}
			List<Object> list = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
			list.addAll((Collection<?>) args[0]);
			return list;
		};
	}

	private static Function mustPrepend() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustPrepend: insufficient arguments");
			}
			return prepend().invoke(args);
		};
	}

	private static Function concat() {
		return (args) -> {
			List<Object> res = new ArrayList<>();
			for (Object arg : args) {
				if (arg instanceof Collection) {
					res.addAll((Collection<?>) arg);
				}
				else if (arg != null) {
					res.add(arg);
				}
			}
			return res;
		};
	}

	private static Function reverse() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Collections.emptyList();
			}
			Object obj = args[0];
			List<Object> result;
			if (obj instanceof Collection) {
				result = new ArrayList<>((Collection<?>) obj);
			}
			else if (obj.getClass().isArray()) {
				int len = Array.getLength(obj);
				result = new ArrayList<>();
				for (int i = 0; i < len; i++) {
					result.add(Array.get(obj, i));
				}
			}
			else if (obj instanceof String) {
				String s = (String) obj;
				return new StringBuilder(s).reverse().toString();
			}
			else {
				result = Collections.singletonList(obj);
			}
			Collections.reverse(result);
			return result;
		};
	}

	private static Function mustReverse() {
		return (args) -> {
			if (args.length == 0) {
				throw new RuntimeException("mustReverse: no arguments provided");
			}
			return reverse().invoke(args);
		};
	}

	private static Function uniq() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Collections.emptyList();
			}
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
		return (args) -> {
			if (args.length == 0) {
				throw new RuntimeException("mustUniq: no arguments provided");
			}
			return uniq().invoke(args);
		};
	}

	private static Function without() {
		return (args) -> {
			if (args.length < 2 || !(args[0] instanceof Collection)) {
				return (args.length > 0) ? args[0] : Collections.emptyList();
			}
			List<Object> result = new ArrayList<>((Collection<?>) args[0]);
			for (int i = 1; i < args.length; i++) {
				result.remove(args[i]);
			}
			return result;
		};
	}

	private static Function mustWithout() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustWithout: insufficient arguments");
			}
			return without().invoke(args);
		};
	}

	private static Function has() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			Object needle = args[0];
			Object haystack = args[1];
			if (haystack instanceof Collection) {
				return ((Collection<?>) haystack).contains(needle);
			}
			return haystack instanceof String && ((String) haystack).contains(String.valueOf(needle));
		};
	}

	private static Function mustHas() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustHas: insufficient arguments");
			}
			boolean result = (boolean) has().invoke(args);
			if (!result) {
				throw new RuntimeException("mustHas: element not found");
			}
			return result;
		};
	}

	private static Function slice() {
		return (args) -> {
			if (args.length < 2) {
				return Collections.emptyList();
			}
			Object list = args[0];
			int from = (args.length > 1) ? ((Number) args[1]).intValue() : 0;
			int to = (args.length > 2) ? ((Number) args[2]).intValue() : Integer.MAX_VALUE;

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
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustSlice: insufficient arguments");
			}
			return slice().invoke(args);
		};
	}

	private static Function until() {
		return (args) -> {
			if (args.length == 0) {
				return Collections.emptyList();
			}
			int n = ((Number) args[0]).intValue();
			List<Integer> res = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				res.add(i);
			}
			return res;
		};
	}

	private static Function untilStep() {
		return (args) -> {
			if (args.length < 3) {
				return Collections.emptyList();
			}
			int start = ((Number) args[0]).intValue();
			int stop = ((Number) args[1]).intValue();
			int step = ((Number) args[2]).intValue();
			if (step == 0) {
				return Collections.emptyList();
			}

			List<Integer> res = new ArrayList<>();
			if (step > 0) {
				for (int i = start; i < stop; i += step) {
					res.add(i);
				}
			}
			else {
				for (int i = start; i > stop; i += step) {
					res.add(i);
				}
			}
			return res;
		};
	}

	private static Function seq() {
		return (args) -> {
			if (args.length == 0) {
				return Collections.emptyList();
			}
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
				for (int i = start; i <= end; i += step) {
					res.add(i);
				}
			}
			else {
				for (int i = start; i >= end; i += step) {
					res.add(i);
				}
			}
			return res;
		};
	}

	private static Function compact() {
		return (args) -> {
			if (args.length == 0 || !(args[0] instanceof Collection)) {
				return Collections.emptyList();
			}
			List<Object> result = new ArrayList<>();
			for (Object item : (Collection<?>) args[0]) {
				if (item != null && !"".equals(item) && !Boolean.FALSE.equals(item)) {
					result.add(item);
				}
			}
			return result;
		};
	}

	private static Function mustCompact() {
		return (args) -> {
			if (args.length == 0) {
				throw new RuntimeException("mustCompact: no arguments provided");
			}
			return compact().invoke(args);
		};
	}

	private static Function sortAlpha() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Collections.emptyList();
			}
			Object obj = args[0];
			List<String> result;
			if (obj instanceof Collection) {
				result = ((Collection<?>) obj).stream().map(String::valueOf).sorted().collect(Collectors.toList());
			}
			else if (obj.getClass().isArray()) {
				int len = Array.getLength(obj);
				result = new ArrayList<>();
				for (int i = 0; i < len; i++) {
					result.add(String.valueOf(Array.get(obj, i)));
				}
				Collections.sort(result);
			}
			else {
				result = Collections.singletonList(String.valueOf(obj));
			}
			return result;
		};
	}

	private static Function split() {
		// Sprig split returns map[string]string with keys "_0", "_1", etc.
		return (args) -> {
			if (args.length < 2) {
				return Map.of();
			}
			String[] parts = String.valueOf(args[1]).split(Pattern.quote(String.valueOf(args[0])));
			Map<String, String> result = new HashMap<>();
			for (int i = 0; i < parts.length; i++) {
				result.put("_" + i, parts[i]);
			}
			return result;
		};
	}

	private static Function splitList() {
		return (args) -> {
			if (args.length < 2) {
				return Collections.emptyList();
			}
			String sep = String.valueOf(args[0]);
			String s = String.valueOf(args[1]);
			return Arrays.asList(s.split(Pattern.quote(sep)));
		};
	}

	private static Function splitn() {
		// Sprig splitn returns map[string]string with keys "_0", "_1", etc.
		return (args) -> {
			if (args.length < 3) {
				return Map.of();
			}
			String sep = String.valueOf(args[0]);
			int n = ((Number) args[1]).intValue();
			String[] parts = String.valueOf(args[2]).split(Pattern.quote(sep), n);
			Map<String, String> result = new HashMap<>();
			for (int i = 0; i < parts.length; i++) {
				result.put("_" + i, parts[i]);
			}
			return result;
		};
	}

	private static Function chunk() {
		return (args) -> {
			if (args.length < 2) {
				return Collections.emptyList();
			}
			int size = ((Number) args[0]).intValue();
			if (size <= 0) {
				return Collections.emptyList();
			}
			Object obj = args[1];
			List<?> list;
			if (obj instanceof List) {
				list = (List<?>) obj;
			}
			else if (obj instanceof Collection) {
				list = new ArrayList<>((Collection<?>) obj);
			}
			else {
				return Collections.emptyList();
			}
			List<List<?>> chunks = new ArrayList<>();
			for (int i = 0; i < list.size(); i += size) {
				chunks.add(list.subList(i, Math.min(i + size, list.size())));
			}
			return chunks;
		};
	}

	private static Function mustChunk() {
		return (args) -> {
			if (args.length < 2) {
				throw new RuntimeException("mustChunk: insufficient arguments");
			}
			return chunk().invoke(args);
		};
	}

	private static Function join() {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			String sep = String.valueOf(args[0]);
			Object listObj = args[1];
			if (listObj instanceof Collection) {
				return ((Collection<?>) listObj).stream().map(String::valueOf).collect(Collectors.joining(sep));
			}
			else if (listObj != null && listObj.getClass().isArray()) {
				int len = Array.getLength(listObj);
				List<String> strs = new ArrayList<>();
				for (int i = 0; i < len; i++) {
					strs.add(String.valueOf(Array.get(listObj, i)));
				}
				return String.join(sep, strs);
			}
			return String.valueOf(listObj);
		};
	}

}
