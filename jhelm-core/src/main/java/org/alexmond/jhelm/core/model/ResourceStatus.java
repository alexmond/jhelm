package org.alexmond.jhelm.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the readiness status of a single Kubernetes resource deployed by a Helm
 * release.
 */
@Data
@Builder
public class ResourceStatus {

	/** Kubernetes resource kind (e.g. {@code Deployment}, {@code Job}). */
	private String kind;

	/** Resource name within its namespace. */
	private String name;

	/** Namespace the resource lives in. */
	private String namespace;

	/**
	 * {@code true} if the resource has reached a ready/complete state; {@code false} if
	 * still pending or failed.
	 */
	private boolean ready;

	/**
	 * Human-readable status message (e.g. {@code "2/3 replicas ready"} or
	 * {@code "ready"}).
	 */
	private String message;

}
