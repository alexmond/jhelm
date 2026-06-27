package org.alexmond.jhelm.core.model;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle status of a Helm release.
 *
 * <p>
 * This enum mirrors Helm's {@code release.Status} type. Each constant carries the exact
 * lowercase, hyphenated wire string that Helm writes into the release record stored in
 * the Kubernetes Secret. Serialization is byte-compatible with Helm so a release created
 * by the Helm CLI can be read back, and a release written by jhelm can be read by the
 * Helm CLI.
 * </p>
 */
public enum ReleaseStatus {

	/** Status is unknown or could not be parsed. */
	UNKNOWN("unknown"),
	/** The release is currently deployed. */
	DEPLOYED("deployed"),
	/** The release has been uninstalled. */
	UNINSTALLED("uninstalled"),
	/** The release has been superseded by a newer revision. */
	SUPERSEDED("superseded"),
	/** The release install/upgrade failed. */
	FAILED("failed"),
	/** The release is in the process of being uninstalled. */
	UNINSTALLING("uninstalling"),
	/** An install is pending. */
	PENDING_INSTALL("pending-install"),
	/** An upgrade is pending. */
	PENDING_UPGRADE("pending-upgrade"),
	/** A rollback is pending. */
	PENDING_ROLLBACK("pending-rollback");

	private final String value;

	ReleaseStatus(String value) {
		this.value = value;
	}

	/**
	 * Returns the exact Helm wire string for this status.
	 * @return the lowercase, hyphenated Helm status string (e.g. {@code "deployed"})
	 */
	@JsonValue
	public String getValue() {
		return this.value;
	}

	/**
	 * Maps a Helm wire string back to a {@link ReleaseStatus}, case-insensitively.
	 *
	 * <p>
	 * Returns {@link #UNKNOWN} for {@code null} or any value not modelled here, so a Helm
	 * release whose status we do not recognise still deserializes instead of throwing.
	 * </p>
	 * @param raw the wire string (any case), may be {@code null}
	 * @return the matching status, or {@link #UNKNOWN} if unrecognised
	 */
	@JsonCreator
	public static ReleaseStatus fromValue(String raw) {
		if (raw == null) {
			return UNKNOWN;
		}
		String normalized = raw.toLowerCase(Locale.ROOT);
		for (ReleaseStatus status : values()) {
			if (status.value.equals(normalized)) {
				return status;
			}
		}
		return UNKNOWN;
	}

}
