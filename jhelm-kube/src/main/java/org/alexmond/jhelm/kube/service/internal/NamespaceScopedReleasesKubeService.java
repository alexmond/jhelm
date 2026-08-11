package org.alexmond.jhelm.kube.service.internal;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.CascadePolicy;
import org.alexmond.jhelm.core.service.KubeService;

/**
 * A {@link KubeService} decorator that scopes release enumeration to a fixed set of
 * namespaces. It overrides only {@link #listAllReleases()} — routing it through the
 * delegate's namespace-scoped {@link KubeService#listReleases(Set)} instead of the
 * cluster-wide secret list — and forwards every other operation unchanged.
 * <p>
 * This lets {@code list --all-namespaces} (and any caller of {@code listAllReleases()})
 * work on a least-privilege / namespace-scoped cluster where the caller holds list
 * permission only within specific namespaces and a cluster-wide {@code secrets:list}
 * would return {@code 403 Forbidden}. Wired as the outermost decorator so each per-
 * namespace {@link KubeService#listReleases(String)} still flows through the retry and
 * metrics decorators underneath.
 */
public class NamespaceScopedReleasesKubeService implements KubeService {

	private final KubeService delegate;

	private final Set<String> namespaces;

	/**
	 * Creates a namespace-scoped release-listing decorator around the given delegate.
	 * @param delegate the underlying {@link KubeService} to which calls are forwarded
	 * @param namespaces the namespaces to which {@link #listAllReleases()} is restricted;
	 * held as an immutable copy
	 */
	public NamespaceScopedReleasesKubeService(KubeService delegate, Set<String> namespaces) {
		this.delegate = delegate;
		this.namespaces = Set.copyOf(namespaces);
	}

	@Override
	public List<Release> listAllReleases() {
		return delegate.listReleases(namespaces);
	}

	@Override
	public void storeRelease(Release release) {
		delegate.storeRelease(release);
	}

	@Override
	public Optional<Release> getRelease(String name, String namespace) {
		return delegate.getRelease(name, namespace);
	}

	@Override
	public List<Release> listReleases(String namespace) {
		return delegate.listReleases(namespace);
	}

	@Override
	public List<Release> listReleases(Set<String> namespaces) {
		return delegate.listReleases(namespaces);
	}

	@Override
	public List<Release> getReleaseHistory(String name, String namespace) {
		return delegate.getReleaseHistory(name, namespace);
	}

	@Override
	public void deleteReleaseHistory(String name, String namespace) {
		delegate.deleteReleaseHistory(name, namespace);
	}

	@Override
	public void pruneReleaseHistory(String name, String namespace, int maxHistory) {
		delegate.pruneReleaseHistory(name, namespace, maxHistory);
	}

	@Override
	public void ensureNamespace(String namespace) {
		delegate.ensureNamespace(namespace);
	}

	@Override
	public void apply(String namespace, String yamlContent) {
		delegate.apply(namespace, yamlContent);
	}

	@Override
	public void applyWithPrune(String namespace, String previousYaml, String yamlContent) {
		delegate.applyWithPrune(namespace, previousYaml, yamlContent);
	}

	@Override
	public void applyDryRun(String namespace, String yamlContent) {
		delegate.applyDryRun(namespace, yamlContent);
	}

	@Override
	public void delete(String namespace, String yamlContent) {
		delegate.delete(namespace, yamlContent);
	}

	@Override
	public void delete(String namespace, String yamlContent, CascadePolicy cascade) {
		delegate.delete(namespace, yamlContent, cascade);
	}

	@Override
	public void waitForDeleted(String namespace, String manifest, int timeoutSeconds) {
		delegate.waitForDeleted(namespace, manifest, timeoutSeconds);
	}

	@Override
	public void restartWorkloads(String namespace, String manifest) {
		delegate.restartWorkloads(namespace, manifest);
	}

	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) {
		return delegate.getResourceStatuses(namespace, manifest);
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) {
		delegate.waitForReady(namespace, manifest, timeoutSeconds);
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds, boolean waitForJobs) {
		delegate.waitForReady(namespace, manifest, timeoutSeconds, waitForJobs);
	}

	@Override
	public Capabilities getCapabilities() {
		return delegate.getCapabilities();
	}

}
