package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Optional;

import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;

/**
 * A {@link KubeService} that owns no cluster connection of its own: it forwards every
 * call to the {@link KubeService} returned by a {@link KubeServiceResolver}, resolved
 * fresh on each invocation. This lets a multi-cluster host route each request to a
 * per-cluster {@link KubeService} without the action layer knowing about cluster
 * selection.
 * <p>
 * The resolver is consulted once per method call, so a request-scoped resolver picks the
 * target cluster for the in-flight request. See {@link KubeServiceResolver} for the
 * wiring and the single-cluster default.
 */
public class DelegatingKubeService implements KubeService {

	private final KubeServiceResolver resolver;

	/**
	 * Creates a delegating service backed by the given resolver.
	 * @param resolver supplies the target {@link KubeService} for each call; must not be
	 * {@code null}
	 */
	public DelegatingKubeService(KubeServiceResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void storeRelease(Release release) {
		resolver.resolve().storeRelease(release);
	}

	@Override
	public Optional<Release> getRelease(String name, String namespace) {
		return resolver.resolve().getRelease(name, namespace);
	}

	@Override
	public List<Release> listReleases(String namespace) {
		return resolver.resolve().listReleases(namespace);
	}

	@Override
	public List<Release> listAllReleases() {
		return resolver.resolve().listAllReleases();
	}

	@Override
	public List<Release> getReleaseHistory(String name, String namespace) {
		return resolver.resolve().getReleaseHistory(name, namespace);
	}

	@Override
	public void deleteReleaseHistory(String name, String namespace) {
		resolver.resolve().deleteReleaseHistory(name, namespace);
	}

	@Override
	public void pruneReleaseHistory(String name, String namespace, int maxHistory) {
		resolver.resolve().pruneReleaseHistory(name, namespace, maxHistory);
	}

	@Override
	public void ensureNamespace(String namespace) {
		resolver.resolve().ensureNamespace(namespace);
	}

	@Override
	public void apply(String namespace, String yamlContent) {
		resolver.resolve().apply(namespace, yamlContent);
	}

	@Override
	public void applyWithPrune(String namespace, String previousYaml, String yamlContent) {
		resolver.resolve().applyWithPrune(namespace, previousYaml, yamlContent);
	}

	@Override
	public void applyDryRun(String namespace, String yamlContent) {
		resolver.resolve().applyDryRun(namespace, yamlContent);
	}

	@Override
	public void delete(String namespace, String yamlContent) {
		resolver.resolve().delete(namespace, yamlContent);
	}

	@Override
	public void delete(String namespace, String yamlContent, CascadePolicy cascade) {
		resolver.resolve().delete(namespace, yamlContent, cascade);
	}

	@Override
	public void waitForDeleted(String namespace, String manifest, int timeoutSeconds) {
		resolver.resolve().waitForDeleted(namespace, manifest, timeoutSeconds);
	}

	@Override
	public void restartWorkloads(String namespace, String manifest) {
		resolver.resolve().restartWorkloads(namespace, manifest);
	}

	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) {
		return resolver.resolve().getResourceStatuses(namespace, manifest);
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) {
		resolver.resolve().waitForReady(namespace, manifest, timeoutSeconds);
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds, boolean waitForJobs) {
		resolver.resolve().waitForReady(namespace, manifest, timeoutSeconds, waitForJobs);
	}

	@Override
	public Capabilities getCapabilities() {
		return resolver.resolve().getCapabilities();
	}

}
