package org.alexmond.jhelm.core.exception;

/**
 * Thrown when a requested release or release revision cannot be found in the cluster.
 */
public class ReleaseNotFoundException extends JhelmException {

	public ReleaseNotFoundException(String message) {
		super(message);
	}

	public static ReleaseNotFoundException forRelease(String releaseName) {
		return new ReleaseNotFoundException("Release not found: " + releaseName);
	}

	public static ReleaseNotFoundException forRelease(String releaseName, String namespace) {
		return new ReleaseNotFoundException("Release not found: " + releaseName + " in namespace " + namespace);
	}

	public static ReleaseNotFoundException forRevision(String releaseName, int revision) {
		return new ReleaseNotFoundException("Revision " + revision + " not found for release " + releaseName);
	}

}
