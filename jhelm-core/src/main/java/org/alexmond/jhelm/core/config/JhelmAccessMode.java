package org.alexmond.jhelm.core.config;

/**
 * Access mode controlling which jhelm operations an adapter exposes.
 *
 * <p>
 * This enum is shared across jhelm adapters so they agree on a single vocabulary. It is
 * consumed per-adapter (currently by jhelm-rest, and later by jhelm-mcp), each of which
 * decides how to enforce it for its own surface.
 */
public enum JhelmAccessMode {

	/**
	 * Exposes only non-mutating operations: template, show, lint, search, get, list,
	 * status and history. Cluster-mutating operations are rejected.
	 */
	READ_ONLY,

	/**
	 * Exposes everything {@link #READ_ONLY} does, plus the cluster-mutating operations:
	 * install, upgrade, uninstall, rollback and test.
	 */
	FULL

}
