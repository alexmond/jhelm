package org.alexmond.jhelm.core.service;

/**
 * A release lifecycle phase delivered to a {@link LifecycleListener}. Mirrors Helm's
 * pre/post hook phases; the {@link #getValue()} string is the Helm wire form (e.g.
 * {@code pre-install}).
 */
public enum LifecyclePhase {

	/** Before an install applies its manifest. */
	PRE_INSTALL("pre-install"),
	/** After an install has applied and stored its release. */
	POST_INSTALL("post-install"),
	/** Before an upgrade applies its manifest. */
	PRE_UPGRADE("pre-upgrade"),
	/** After an upgrade has applied and stored its release. */
	POST_UPGRADE("post-upgrade"),
	/** Before a rollback applies. */
	PRE_ROLLBACK("pre-rollback"),
	/** After a rollback has applied. */
	POST_ROLLBACK("post-rollback"),
	/** Before an uninstall deletes resources. */
	PRE_DELETE("pre-delete"),
	/** After an uninstall has deleted resources. */
	POST_DELETE("post-delete");

	private final String value;

	LifecyclePhase(String value) {
		this.value = value;
	}

	/**
	 * @return the Helm wire string for this phase (e.g. {@code pre-install})
	 */
	public String getValue() {
		return this.value;
	}

}
