package org.alexmond.jhelm.core.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements Helm's {@code .Values} template object. Helm exposes {@code .Values} as
 * {@code chartutil.Values}, a {@code map[string]interface{}} that <em>also</em> carries
 * an {@code AsMap()} method returning the underlying map.
 *
 * <p>
 * Charts transpiled by {@code gotohelm} (e.g. redpanda's console and operator) rely on
 * {@code {{ $dot.Values.AsMap }}} to obtain the raw values map. Modelling {@code .Values}
 * as a plain map made {@code .AsMap} an absent key that resolved to {@code nil}, so every
 * value read downstream came back null. Like {@link ChartFiles}, this is a map that also
 * exposes a helper method; the template executor resolves {@code AsMap} by name (falling
 * back from map-key lookup) once the key is absent.
 *
 * <p>
 * <strong>Mutability contract (1.0):</strong> {@code Values} extends {@link HashMap} so
 * the template executor can resolve {@code .Values.foo} through the {@link Map}
 * interface, which means it structurally inherits the mutating map methods. In practice
 * it is built once from the already-merged values and only read during rendering — jhelm
 * never mutates it after construction, and callers should treat it as read-only. The
 * {@code HashMap} superclass is an interop detail (so it <em>is-a</em> {@code Map} for
 * the engine), not an invitation to mutate.
 */
@SuppressWarnings("PMD.MethodNamingConventions")
public class Values extends HashMap<String, Object> {

	public Values(Map<String, Object> values) {
		super(values);
	}

	/**
	 * @return the underlying values map (this instance), matching
	 * {@code chartutil.Values.AsMap()}
	 */
	public Map<String, Object> AsMap() {
		return this;
	}

}
