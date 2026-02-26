package org.alexmond.jhelm.core.exception;

import java.util.List;

import org.alexmond.jhelm.core.model.ResourceStatus;

/**
 * Thrown when a wait-for-ready operation exceeds the configured timeout. Carries the
 * last-known resource statuses so callers can inspect what was not ready.
 */
public class WaitTimeoutException extends JhelmException {

	private final List<ResourceStatus> pendingResources;

	public WaitTimeoutException(String message, List<ResourceStatus> pendingResources) {
		super(message);
		this.pendingResources = pendingResources;
	}

	/**
	 * Returns the resources that were not ready when the timeout elapsed.
	 */
	public List<ResourceStatus> getPendingResources() {
		return pendingResources;
	}

}
