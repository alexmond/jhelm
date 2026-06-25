package org.alexmond.jhelm.core.exception;

/**
 * Thrown when a deployment (install or upgrade) fails after resources have been partially
 * applied. Contains the manifest that was applied so callers can attempt cleanup.
 */
public class DeploymentFailedException extends JhelmException {

	private final String appliedManifest;

	/**
	 * Creates a new deployment failure.
	 * @param message a description of what failed
	 * @param cause the underlying error that triggered the failure
	 * @param appliedManifest the manifest that was applied before the failure, used for
	 * cleanup
	 */
	public DeploymentFailedException(String message, Throwable cause, String appliedManifest) {
		super(message, cause);
		this.appliedManifest = appliedManifest;
	}

	/**
	 * Returns the manifest that was (partially) applied before the failure, or
	 * {@code null} if nothing was applied.
	 * @return the partially applied manifest, or {@code null}
	 */
	public String getAppliedManifest() {
		return appliedManifest;
	}

}
