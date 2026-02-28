package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Sprig Collection manipulation functions (lists, slices, dicts). Delegates to
 * {@link ListFunctions} and {@link DictFunctions}.
 *
 * @see <a href="https://masterminds.github.io/sprig/lists.html">Sprig Lists</a>
 * @see <a href="https://masterminds.github.io/sprig/dicts.html">Sprig Dicts</a>
 */
public final class CollectionFunctions {

	private CollectionFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();
		functions.putAll(ListFunctions.getFunctions());
		functions.putAll(DictFunctions.getFunctions());
		return functions;
	}

}
