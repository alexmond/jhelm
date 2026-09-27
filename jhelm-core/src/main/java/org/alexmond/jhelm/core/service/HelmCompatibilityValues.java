package org.alexmond.jhelm.core.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helm 4's values pruning (#828).
 * <p>
 * Helm 4 drops null-valued keys from the coalesced {@code .Values} tree, so
 * {@code toYaml .Values.some.map} renders {@code {}} where Helm 3 renders
 * {@code key: null}. Verified against helm v4.3.0 and v3.14.2: the conversion functions
 * themselves are <em>unchanged</em> between the two lines — {@code dict "x" nil | toYaml}
 * and {@code "a:" | fromYaml | toYaml} both still emit {@code null} under Helm 4 — so the
 * difference lives in the values tree alone, not in {@code toYaml}/{@code toJson}.
 * <p>
 * Pruning walks maps only. Helm's coalescing does not descend into lists, and helm 4.3.0
 * keeps {@code - a: null} inside a list, so list elements are left exactly as they are.
 * <p>
 * It applies to the release chart's merged values (depth 0), after subchart defaults have
 * been folded in and before the values are sliced back down to subcharts. Three
 * behaviours pin that placement, all verified against helm v4.3.0 and v3.14.2:
 * <ul>
 * <li>Nulls from the release chart's own {@code values.yaml} <em>and</em> from a
 * subchart's defaults are both omitted from {@code .Values} (helm 4.3.0 renders
 * {@code {}} where helm 3 renders {@code key: null}) — grafana/tempo and several bitnami
 * charts rely on the subchart half.</li>
 * <li>A <em>user</em> null ({@code -f}/{@code --set}) is NOT pruned: helm 4.3.0 keeps the
 * key ({@code hasKey} true) so it still overrides a subchart default, where helm 3.14.2
 * dropped it. Such nulls are re-applied after pruning.</li>
 * <li>A subchart's own null still deletes its subchart's default, because the subchart
 * re-merges its unpruned {@code values.yaml} when it renders at depth &gt; 0:
 * signoz/signoz drops bitnami zookeeper's {@code docker.io}, while signoz/clickhouse as
 * the release chart keeps it.</li>
 * </ul>
 */
final class HelmCompatibilityValues {

	private HelmCompatibilityValues() {
	}

	/**
	 * Prunes null-valued keys from the release chart's merged values when the mode calls
	 * for it, keeping the user's own null overrides.
	 * @param values the merged values (not modified)
	 * @param userValues the user-supplied overrides whose nulls must survive, or
	 * {@code null}
	 * @param compatibility the Helm line being rendered
	 * @param depth the chart depth; only the release chart (0) is pruned
	 * @return the pruned map under Helm 4 at depth 0, else {@code values} unchanged
	 */
	static Map<String, Object> pruneIfNeeded(Map<String, Object> values, Map<String, Object> userValues,
			HelmCompatibility compatibility, int depth) {
		if (values == null || depth != 0 || !compatibility.prunesNullValues()) {
			return values;
		}
		Map<String, Object> pruned = prune(values);
		reapplyUserNulls(pruned, userValues);
		return pruned;
	}

	/**
	 * Puts back the nulls the user asked for, which Helm 4 keeps as explicit overrides.
	 * @param target the pruned values, modified in place
	 * @param userValues the user-supplied overrides, or {@code null}
	 */
	private static void reapplyUserNulls(Map<String, Object> target, Map<String, Object> userValues) {
		if (userValues == null) {
			return;
		}
		userValues.forEach((key, value) -> {
			if (value == null) {
				target.put(key, null);
			}
			else if (value instanceof Map<?, ?> nested && target.get(key) instanceof Map<?, ?> existing) {
				@SuppressWarnings("unchecked")
				Map<String, Object> typedNested = (Map<String, Object>) nested;
				@SuppressWarnings("unchecked")
				Map<String, Object> typedExisting = (Map<String, Object>) existing;
				reapplyUserNulls(typedExisting, typedNested);
			}
		});
	}

	private static Map<String, Object> prune(Map<String, Object> values) {
		Map<String, Object> kept = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			if (value == null) {
				return;
			}
			if (value instanceof Map<?, ?> nested) {
				@SuppressWarnings("unchecked")
				Map<String, Object> typed = (Map<String, Object>) nested;
				kept.put(key, prune(typed));
			}
			else {
				kept.put(key, value);
			}
		});
		return kept;
	}

}
