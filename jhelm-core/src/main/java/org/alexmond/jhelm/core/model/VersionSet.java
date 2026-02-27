package org.alexmond.jhelm.core.model;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A list of Kubernetes API group versions that supports the {@code Has(String)} method
 * used by Helm chart templates via {@code .Capabilities.APIVersions.Has}.
 * <p>
 * In Go's Helm source, this is {@code chartutil.VersionSet} which is {@code []string}
 * with a {@code Has(v string) bool} method. Go templates can call methods on values, so
 * {@code .Capabilities.APIVersions.Has "policy/v1"} invokes the method. This class
 * mirrors that behavior for jhelm's template executor which uses Java reflection to find
 * and invoke methods.
 */
public class VersionSet extends ArrayList<String> {

	public VersionSet(Collection<String> versions) {
		super(versions);
	}

	/**
	 * Check whether the given API version is in this set.
	 * <p>
	 * Called from Go templates as {@code .Capabilities.APIVersions.Has "policy/v1"}. The
	 * method name must be uppercase to match Go convention.
	 * @param version the API group version string to check
	 * @return {@code true} if the version is present
	 */
	@SuppressWarnings({ "checkstyle:MethodName", "PMD.MethodNamingConventions" })
	public boolean Has(String version) {
		return contains(version);
	}

}
