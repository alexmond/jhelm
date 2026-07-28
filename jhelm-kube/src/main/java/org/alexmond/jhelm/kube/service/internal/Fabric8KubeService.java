package org.alexmond.jhelm.kube.service.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.exception.ReleaseStorageException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.HelmReleaseCodec;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.CascadePolicy;
import org.alexmond.jhelm.core.service.KubeService;
import org.yaml.snakeyaml.LoaderOptions;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * {@link KubeService} implementation backed by the Fabric8 Kubernetes client, at
 * behavioral parity with the official-client {@link HelmKubeService}: it stores/reads
 * Helm release Secrets in the same {@code sh.helm.release.v1.*} format, applies and
 * deletes rendered manifests via server-side apply, and checks/waits on resource
 * readiness with the same rules.
 *
 * <p>
 * The release payload, readiness rules, manifest parsing, and every Helm-visible constant
 * are identical to the official-client backend. The Kubernetes calls differ; where the
 * two clients disagree at the wire boundary — chiefly that Fabric8's
 * {@code Secret.getData()} returns the base64 wire string rather than decoded bytes — the
 * difference is handled here so on-cluster state matches Helm exactly.
 */
@Slf4j
public class Fabric8KubeService implements KubeService {

	private static final String RELEASE_SECRET_TYPE = "helm.sh/release.v1";

	private static final String RELEASE_DATA_KEY = "release";

	private static final String OWNER_LABEL = "owner";

	private static final String OWNER_VALUE = "helm";

	private static final String NAME_LABEL = "name";

	private static final String STATUS_LABEL = "status";

	private static final String VERSION_LABEL = "version";

	private static final Set<String> RESERVED_RELEASE_LABELS = Set.of(OWNER_LABEL, NAME_LABEL, STATUS_LABEL,
			VERSION_LABEL);

	private static final String FIELD_MANAGER = "helm";

	private static final String RESTART_ANNOTATION = "kubectl.kubernetes.io/restartedAt";

	private static final Set<String> RESTARTABLE_KINDS = Set.of("Deployment", "StatefulSet", "DaemonSet");

	// Cluster-scoped kinds get no namespace on apply/delete/read; everything else is
	// treated as namespaced (matching the official-client backend's default for unknown
	// kinds, which covers the common namespaced-CRD case).
	private static final Set<String> CLUSTER_SCOPED_KINDS = Set.of("Namespace", "Node", "PersistentVolume",
			"ClusterRole", "ClusterRoleBinding", "StorageClass", "CustomResourceDefinition", "PriorityClass",
			"MutatingWebhookConfiguration", "ValidatingWebhookConfiguration", "APIService", "CSIDriver", "CSINode",
			"VolumeAttachment", "RuntimeClass", "PodSecurityPolicy", "IngressClass");

	private static final long WAIT_READY_POLL_MS = 5000;

	private static final long WAIT_DELETED_POLL_MS = 2000;

	private final KubernetesClient client;

	private final HelmReleaseCodec releaseCodec = new HelmReleaseCodec();

	/**
	 * Creates the service backed by the given Fabric8 client.
	 * @param client the configured Fabric8 Kubernetes client
	 */
	public Fabric8KubeService(KubernetesClient client) {
		this.client = client;
	}

	// ---------------------------------------------------------------- release storage

	@Override
	public void storeRelease(Release release) {
		String secretName = "sh.helm.release.v1." + release.getName() + ".v" + release.getVersion();
		String namespace = release.getNamespace();
		try {
			Map<String, String> labels = new LinkedHashMap<>();
			labels.put(OWNER_LABEL, OWNER_VALUE);
			labels.put(NAME_LABEL, release.getName());
			if (release.getInfo() != null && release.getInfo().getStatus() != null) {
				labels.put(STATUS_LABEL, release.getInfo().getStatus().getValue());
			}
			labels.put(VERSION_LABEL, String.valueOf(release.getVersion()));
			if (release.getLabels() != null) {
				release.getLabels().forEach((key, value) -> {
					if (!RESERVED_RELEASE_LABELS.contains(key)) {
						labels.put(key, value);
					}
				});
			}
			Secret secret = new SecretBuilder().withNewMetadata()
				.withName(secretName)
				.withNamespace(namespace)
				.withLabels(labels)
				.endMetadata()
				.withType(RELEASE_SECRET_TYPE)
				.addToStringData(RELEASE_DATA_KEY, encodeRelease(release))
				.build();
			createOrReplaceSecret(namespace, secret);
		}
		catch (RuntimeException ex) {
			throw new ReleaseStorageException("Failed to store release: " + release.getName(), ex);
		}
	}

	private void createOrReplaceSecret(String namespace, Secret secret) {
		try {
			this.client.secrets().inNamespace(namespace).resource(secret).create();
		}
		catch (KubernetesClientException ex) {
			if (ex.getCode() == 409) {
				this.client.secrets().inNamespace(namespace).resource(secret).update();
			}
			else {
				throw ex;
			}
		}
	}

	@Override
	public Optional<Release> getRelease(String name, String namespace) {
		List<Secret> secrets = listSecrets(namespace, ownerNameSelector(name), "get release " + name);
		return secrets.stream().max(Comparator.comparingInt(Fabric8KubeService::versionOf)).map(this::decodeRelease);
	}

	@Override
	public List<Release> listReleases(String namespace) {
		return toLatestReleases(listSecrets(namespace, Map.of(OWNER_LABEL, OWNER_VALUE), "list releases"));
	}

	@Override
	public List<Release> listAllReleases() {
		List<Secret> secrets;
		try {
			secrets = this.client.secrets().inAnyNamespace().withLabel(OWNER_LABEL, OWNER_VALUE).list().getItems();
		}
		catch (KubernetesClientException ex) {
			throw new KubernetesOperationException("Failed to list all releases" + describeClientFailure(ex), ex,
					ex.getCode());
		}
		return toLatestReleases(secrets);
	}

	private List<Release> toLatestReleases(List<Secret> secrets) {
		Map<String, Secret> latest = new LinkedHashMap<>();
		for (Secret secret : secrets) {
			Map<String, String> labels = labelsOf(secret);
			String key = secret.getMetadata().getNamespace() + "/" + labels.get(NAME_LABEL);
			Secret current = latest.get(key);
			if (current == null || versionOf(secret) > versionOf(current)) {
				latest.put(key, secret);
			}
		}
		List<Release> releases = new ArrayList<>();
		for (Secret secret : latest.values()) {
			try {
				releases.add(decodeRelease(secret));
			}
			catch (ReleaseStorageException ex) {
				log.warn("Skipping unreadable release secret {}: {}", secret.getMetadata().getName(), ex.getMessage());
			}
		}
		return releases;
	}

	@Override
	public List<Release> getReleaseHistory(String name, String namespace) {
		return listSecrets(namespace, ownerNameSelector(name), "get release history for " + name).stream()
			.sorted(Comparator.comparingInt(Fabric8KubeService::versionOf).reversed())
			.map(this::decodeRelease)
			.toList();
	}

	@Override
	public void deleteReleaseHistory(String name, String namespace) {
		try {
			for (Secret secret : listSecrets(namespace, ownerNameSelector(name),
					"delete release history for " + name)) {
				this.client.secrets().inNamespace(namespace).withName(secret.getMetadata().getName()).delete();
			}
		}
		catch (KubernetesClientException ex) {
			throw new KubernetesOperationException("Failed to delete release history for " + name, ex, ex.getCode());
		}
	}

	@Override
	public void pruneReleaseHistory(String name, String namespace, int maxHistory) {
		if (maxHistory <= 0) {
			return;
		}
		try {
			List<Secret> sorted = listSecrets(namespace, ownerNameSelector(name), "prune release history for " + name)
				.stream()
				.sorted(Comparator.comparingInt(Fabric8KubeService::versionOf))
				.toList();
			int toDelete = sorted.size() - maxHistory;
			if (toDelete <= 0) {
				return;
			}
			for (Secret secret : sorted.subList(0, toDelete)) {
				this.client.secrets().inNamespace(namespace).withName(secret.getMetadata().getName()).delete();
			}
		}
		catch (KubernetesClientException ex) {
			throw new KubernetesOperationException("Failed to prune release history for " + name, ex, ex.getCode());
		}
	}

	private List<Secret> listSecrets(String namespace, Map<String, String> selector, String operation) {
		try {
			return this.client.secrets().inNamespace(namespace).withLabels(selector).list().getItems();
		}
		catch (KubernetesClientException ex) {
			throw new KubernetesOperationException("Failed to " + operation + describeClientFailure(ex), ex,
					ex.getCode());
		}
	}

	/**
	 * Builds a human-readable suffix describing a {@link KubernetesClientException},
	 * appending the client's message so the cluster's own reason (an RBAC denial, a
	 * rate-limit note) is visible on the thrown message rather than only on the cause.
	 * @param ex the fabric8 client exception
	 * @return a suffix beginning with {@code ": "}, or an empty string if no detail is
	 * available
	 */
	private static String describeClientFailure(KubernetesClientException ex) {
		return (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ": " + ex.getMessage() : "";
	}

	private static Map<String, String> ownerNameSelector(String name) {
		Map<String, String> selector = new LinkedHashMap<>();
		selector.put(OWNER_LABEL, OWNER_VALUE);
		selector.put(NAME_LABEL, name);
		return selector;
	}

	private static Map<String, String> labelsOf(Secret secret) {
		Map<String, String> labels = secret.getMetadata().getLabels();
		return (labels != null) ? labels : Map.of();
	}

	private static int versionOf(Secret secret) {
		String version = labelsOf(secret).get(VERSION_LABEL);
		return (version != null) ? Integer.parseInt(version) : 0;
	}

	// ---------------------------------------------------------------- payload codec

	private String encodeRelease(Release release) {
		byte[] json = this.releaseCodec.toJson(release);
		byte[] gzipped = gzip(json);
		// Inner base64; Fabric8 base64-encodes stringData again for the wire (Helm's
		// double base64).
		return Base64.getEncoder().encodeToString(gzipped);
	}

	private Release decodeRelease(Secret secret) {
		try {
			// Fabric8 getData() returns the base64 wire string (client-java auto-decodes
			// the
			// outer layer). Decode the outer layer to recover the inner base64, then the
			// inner layer to recover the gzipped JSON.
			String wire = secret.getData().get(RELEASE_DATA_KEY);
			byte[] innerBase64 = Base64.getDecoder().decode(wire);
			byte[] gzipped = Base64.getDecoder().decode(innerBase64);
			byte[] json = gunzip(gzipped);
			return this.releaseCodec.fromJson(json);
		}
		catch (RuntimeException ex) {
			throw new ReleaseStorageException("Failed to decode release from Secret: " + secret.getMetadata().getName(),
					ex);
		}
	}

	private static byte[] gzip(byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(data);
		}
		catch (IOException ex) {
			throw new ReleaseStorageException("Failed to gzip release payload", ex);
		}
		return out.toByteArray();
	}

	private static byte[] gunzip(byte[] data) {
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
			return gzip.readAllBytes();
		}
		catch (IOException ex) {
			throw new ReleaseStorageException("Failed to gunzip release payload", ex);
		}
	}

	// ---------------------------------------------------------------- namespace

	@Override
	public void ensureNamespace(String namespace) {
		try {
			Namespace ns = new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build();
			log.info("Creating namespace {}", namespace);
			this.client.namespaces().resource(ns).create();
		}
		catch (KubernetesClientException ex) {
			if (ex.getCode() == 409) {
				return;
			}
			throw new KubernetesOperationException("Failed to create namespace " + namespace, ex, ex.getCode());
		}
	}

	// ---------------------------------------------------------------- apply

	@Override
	public void apply(String namespace, String yamlContent) {
		applyManifest(namespace, yamlContent, false);
	}

	@Override
	public void applyDryRun(String namespace, String yamlContent) {
		applyManifest(namespace, yamlContent, true);
	}

	private void applyManifest(String namespace, String yamlContent, boolean serverDryRun) {
		try {
			for (Object doc : loadUnstructured(yamlContent)) {
				if (doc instanceof Map<?, ?> map) {
					applyResource(namespace, map, serverDryRun);
				}
			}
		}
		catch (KubernetesClientException ex) {
			throw new KubernetesOperationException("Failed to apply manifest", ex, ex.getCode());
		}
		catch (RuntimeException ex) {
			throw new KubernetesOperationException("Failed to apply manifest", ex);
		}
	}

	private void applyResource(String namespace, Map<?, ?> resource, boolean serverDryRun) {
		String[] id = identify(resource);
		String kind = id[1];
		String name = id[2];
		PatchContext.Builder ctx = new PatchContext.Builder().withPatchType(PatchType.SERVER_SIDE_APPLY)
			.withFieldManager(FIELD_MANAGER)
			.withForce(true);
		if (serverDryRun) {
			ctx.withDryRun(List.of("All"));
		}
		log.info("{} {} {}", (serverDryRun) ? "Validating (server dry-run)" : "Applying", kind, name);
		// Deserialize the doc string with Fabric8's own reader so the resource serializes
		// cleanly for server-side apply; a GenericKubernetesResource built from a raw map
		// fails to serialize its additionalProperties (Jackson keySerializer NPE).
		NamespaceableResource<HasMetadata> op = this.client.resource(dumpYaml(resource));
		if (isNamespaced(kind)) {
			op.inNamespace(namespace).patch(ctx.build());
		}
		else {
			op.patch(ctx.build());
		}
	}

	// ---------------------------------------------------------------- delete

	@Override
	public void delete(String namespace, String yamlContent) {
		delete(namespace, yamlContent, CascadePolicy.BACKGROUND);
	}

	@Override
	public void delete(String namespace, String yamlContent, CascadePolicy cascade) {
		try {
			for (Object doc : loadUnstructured(yamlContent)) {
				if (doc instanceof Map<?, ?> map) {
					deleteResource(namespace, map, cascade);
				}
			}
		}
		catch (KubernetesClientException ex) {
			throw new KubernetesOperationException("Failed to delete manifest", ex, ex.getCode());
		}
		catch (RuntimeException ex) {
			throw new KubernetesOperationException("Failed to delete manifest", ex);
		}
	}

	private void deleteResource(String namespace, Map<?, ?> resource, CascadePolicy cascade) {
		String[] id = identify(resource);
		String kind = id[1];
		String name = id[2];
		GenericKubernetesResource gkr = toGeneric(resource);
		if (isNamespaced(kind)) {
			metaOf(gkr).setNamespace(namespace);
		}
		log.info("Deleting {} {}", kind, name);
		// Fabric8 delete tolerates an absent resource (returns no details) — matches the
		// official-client backend's 404 tolerance.
		this.client.resource(gkr).withPropagationPolicy(propagationOf(cascade)).delete();
	}

	private static DeletionPropagation propagationOf(CascadePolicy cascade) {
		return switch (cascade) {
			case FOREGROUND -> DeletionPropagation.FOREGROUND;
			case ORPHAN -> DeletionPropagation.ORPHAN;
			case BACKGROUND -> DeletionPropagation.BACKGROUND;
		};
	}

	// ---------------------------------------------------------------- wait for deleted

	@Override
	public void waitForDeleted(String namespace, String manifest, int timeoutSeconds) {
		long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
		List<Map<?, ?>> resources = parseResources(manifest);
		while (true) {
			List<String> remaining = stillPresent(namespace, resources);
			if (remaining.isEmpty()) {
				return;
			}
			long left = deadline - System.currentTimeMillis();
			if (left <= 0) {
				throw new WaitTimeoutException(
						"Timeout waiting for resources to be deleted: " + String.join(", ", remaining), List.of());
			}
			remaining.forEach((res) -> log.info("Waiting for {} to be deleted", res));
			sleep(Math.min(WAIT_DELETED_POLL_MS, left), "Interrupted while waiting for resources to be deleted");
		}
	}

	private List<String> stillPresent(String namespace, List<Map<?, ?>> resources) {
		List<String> present = new ArrayList<>();
		for (Map<?, ?> resource : resources) {
			String[] id = identify(resource);
			GenericKubernetesResource gkr = toGeneric(resource);
			if (isNamespaced(id[1])) {
				metaOf(gkr).setNamespace(namespace);
			}
			try {
				if (this.client.resource(gkr).get() != null) {
					present.add(id[1] + "/" + id[2]);
				}
			}
			catch (KubernetesClientException ex) {
				throw new KubernetesOperationException("Failed while waiting for resources to be deleted", ex,
						ex.getCode());
			}
		}
		return present;
	}

	// ---------------------------------------------------------------- restart workloads

	@Override
	public void restartWorkloads(String namespace, String manifest) {
		String restartedAt = OffsetDateTime.now().toString();
		try {
			for (Object doc : loadUnstructured(manifest)) {
				if (doc instanceof Map<?, ?> map) {
					String[] id = identify(map);
					if (RESTARTABLE_KINDS.contains(id[1])) {
						restartWorkload(namespace, id, restartedAt);
					}
				}
			}
		}
		catch (KubernetesClientException ex) {
			throw new KubernetesOperationException("Failed to restart workloads", ex, ex.getCode());
		}
		catch (RuntimeException ex) {
			throw new KubernetesOperationException("Failed to restart workloads", ex);
		}
	}

	private void restartWorkload(String namespace, String[] id, String restartedAt) {
		String patchJson = "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"" + RESTART_ANNOTATION + "\":\""
				+ restartedAt + "\"}}}}}";
		// Identity-only target (GVK + name + namespace). A full GenericKubernetesResource
		// would be serialized for the strategic-merge patch and trip the
		// additionalProperties
		// keySerializer NPE; the patch body itself is patchJson.
		GenericKubernetesResource gkr = identityResource(id, namespace);
		PatchContext ctx = new PatchContext.Builder().withPatchType(PatchType.STRATEGIC_MERGE)
			.withFieldManager(FIELD_MANAGER)
			.build();
		this.client.resource(gkr).patch(ctx, patchJson);
		log.info("Restarted workload {}/{}", id[1], id[2]);
	}

	private static GenericKubernetesResource identityResource(String[] id, String namespace) {
		GenericKubernetesResource gkr = new GenericKubernetesResource();
		gkr.setApiVersion(id[0]);
		gkr.setKind(id[1]);
		ObjectMeta meta = new ObjectMeta();
		meta.setName(id[2]);
		meta.setNamespace(namespace);
		gkr.setMetadata(meta);
		return gkr;
	}

	// ---------------------------------------------------------------- status / readiness

	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) {
		List<ResourceStatus> statuses = new ArrayList<>();
		Iterable<Object> docs;
		try {
			docs = loadUnstructured(manifest);
		}
		catch (RuntimeException ex) {
			throw new KubernetesOperationException("Failed to parse manifest", ex);
		}
		for (Object doc : docs) {
			if (doc instanceof Map<?, ?> map) {
				statuses.add(checkResourceStatus(namespace, map));
			}
		}
		return statuses;
	}

	private ResourceStatus checkResourceStatus(String namespace, Map<?, ?> resource) {
		String[] id = identify(resource);
		String kind = id[1];
		String name = id[2];
		try {
			return switch (kind) {
				case "Deployment" -> checkDeployment(namespace, name);
				case "ReplicaSet" -> checkReplicaSet(namespace, name);
				case "DaemonSet" -> checkDaemonSet(namespace, name);
				case "StatefulSet" -> checkStatefulSet(namespace, name);
				case "Job" -> checkJob(namespace, name);
				case "Pod" -> checkPod(namespace, name);
				default -> ResourceStatus.builder()
					.kind(kind)
					.name(name)
					.namespace(namespace)
					.ready(true)
					.message("ready")
					.build();
			};
		}
		catch (RuntimeException ex) {
			return ResourceStatus.builder()
				.kind(kind)
				.name(name)
				.namespace(namespace)
				.ready(false)
				.message(ex.getMessage())
				.build();
		}
	}

	private ResourceStatus checkDeployment(String namespace, String name) {
		Deployment dep = this.client.apps().deployments().inNamespace(namespace).withName(name).get();
		if (dep == null) {
			return notFound("Deployment", name, namespace);
		}
		var spec = dep.getSpec();
		var st = dep.getStatus();
		int desired = ((spec != null) && (spec.getReplicas() != null)) ? spec.getReplicas() : 1;
		int ready = nz((st != null) ? st.getReadyReplicas() : null);
		int updated = nz((st != null) ? st.getUpdatedReplicas() : null);
		int available = nz((st != null) ? st.getAvailableReplicas() : null);
		boolean observed = observedCurrent(gen(generationOf(dep.getMetadata())),
				(st != null) ? st.getObservedGeneration() : null);
		boolean isReady = observed && (updated >= desired) && (ready >= desired) && (available >= desired);
		String message = (!observed) ? "waiting for rollout"
				: (isReady) ? "ready" : String.format("%d/%d replicas ready", ready, desired);
		return status("Deployment", name, namespace, isReady, message);
	}

	private ResourceStatus checkReplicaSet(String namespace, String name) {
		ReplicaSet rs = this.client.apps().replicaSets().inNamespace(namespace).withName(name).get();
		if (rs == null) {
			return notFound("ReplicaSet", name, namespace);
		}
		var spec = rs.getSpec();
		var st = rs.getStatus();
		int desired = ((spec != null) && (spec.getReplicas() != null)) ? spec.getReplicas() : 1;
		int ready = nz((st != null) ? st.getReadyReplicas() : null);
		boolean observed = observedCurrent(gen(generationOf(rs.getMetadata())),
				(st != null) ? st.getObservedGeneration() : null);
		boolean isReady = observed && (ready >= desired);
		String message = (!observed) ? "waiting for rollout"
				: (isReady) ? "ready" : String.format("%d/%d replicas ready", ready, desired);
		return status("ReplicaSet", name, namespace, isReady, message);
	}

	private ResourceStatus checkDaemonSet(String namespace, String name) {
		DaemonSet ds = this.client.apps().daemonSets().inNamespace(namespace).withName(name).get();
		if (ds == null) {
			return notFound("DaemonSet", name, namespace);
		}
		var st = ds.getStatus();
		int desired = nz((st != null) ? st.getDesiredNumberScheduled() : null);
		int updated = nz((st != null) ? st.getUpdatedNumberScheduled() : null);
		int ready = nz((st != null) ? st.getNumberReady() : null);
		boolean observed = observedCurrent(gen(generationOf(ds.getMetadata())),
				(st != null) ? st.getObservedGeneration() : null);
		boolean isReady = observed && (updated >= desired) && (ready >= desired);
		String message = (isReady) ? "ready" : String.format("%d/%d ready", ready, desired);
		return status("DaemonSet", name, namespace, isReady, message);
	}

	private ResourceStatus checkStatefulSet(String namespace, String name) {
		StatefulSet ss = this.client.apps().statefulSets().inNamespace(namespace).withName(name).get();
		if (ss == null) {
			return notFound("StatefulSet", name, namespace);
		}
		var spec = ss.getSpec();
		var st = ss.getStatus();
		int desired = ((spec != null) && (spec.getReplicas() != null)) ? spec.getReplicas() : 1;
		int ready = nz((st != null) ? st.getReadyReplicas() : null);
		boolean isReady = ready >= desired;
		String message = (isReady) ? "ready" : String.format("%d/%d replicas ready", ready, desired);
		return status("StatefulSet", name, namespace, isReady, message);
	}

	private ResourceStatus checkJob(String namespace, String name) {
		Job job = this.client.batch().v1().jobs().inNamespace(namespace).withName(name).get();
		if (job == null) {
			return notFound("Job", name, namespace);
		}
		var spec = job.getSpec();
		var st = job.getStatus();
		int completions = ((spec != null) && (spec.getCompletions() != null)) ? spec.getCompletions() : 1;
		int succeeded = nz((st != null) ? st.getSucceeded() : null);
		int failed = nz((st != null) ? st.getFailed() : null);
		boolean isReady = succeeded >= completions;
		String message = (isReady) ? "complete"
				: (failed > 0) ? String.format("failed=%d, succeeded=%d/%d", failed, succeeded, completions)
						: String.format("succeeded=%d/%d", succeeded, completions);
		return status("Job", name, namespace, isReady, message);
	}

	private ResourceStatus checkPod(String namespace, String name) {
		Pod pod = this.client.pods().inNamespace(namespace).withName(name).get();
		if (pod == null) {
			return notFound("Pod", name, namespace);
		}
		var st = pod.getStatus();
		String phase = (st != null) ? st.getPhase() : null;
		boolean readyCondition = (st != null) && (st.getConditions() != null)
				&& st.getConditions()
					.stream()
					.anyMatch((c) -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
		boolean isReady = isPodReady(phase, readyCondition);
		boolean terminal = "Failed".equals(phase);
		return ResourceStatus.builder()
			.kind("Pod")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.terminal(terminal)
			.message((isReady) ? "ready" : phase)
			.build();
	}

	private static Long generationOf(ObjectMeta meta) {
		return (meta != null) ? meta.getGeneration() : null;
	}

	private static boolean isPodReady(String phase, boolean readyCondition) {
		return "Succeeded".equals(phase) || ("Running".equals(phase) && readyCondition);
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) {
		long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
		while (true) {
			List<ResourceStatus> statuses = getResourceStatuses(namespace, manifest);
			if (statuses.stream().allMatch(ResourceStatus::isReady)) {
				return;
			}
			List<ResourceStatus> failed = statuses.stream().filter((s) -> s.isTerminal() && !s.isReady()).toList();
			if (!failed.isEmpty()) {
				throw new WaitTimeoutException("Resource(s) terminally failed: " + describe(failed), failed);
			}
			long left = deadline - System.currentTimeMillis();
			if (left <= 0) {
				break;
			}
			statuses.stream()
				.filter((s) -> !s.isReady())
				.forEach((s) -> log.info("Waiting for {}/{}: {}", s.getKind(), s.getName(), s.getMessage()));
			sleep(Math.min(WAIT_READY_POLL_MS, left), "Interrupted while waiting for resources to be ready");
		}
		List<ResourceStatus> notReady = getResourceStatuses(namespace, manifest).stream()
			.filter((s) -> !s.isReady())
			.toList();
		if (!notReady.isEmpty()) {
			throw new WaitTimeoutException("Timeout waiting for resources to be ready: " + describe(notReady),
					notReady);
		}
	}

	@Override
	public Capabilities getCapabilities() {
		try {
			var version = this.client.getKubernetesVersion();
			String gitVersion = (version != null) ? version.getGitVersion() : null;
			if (gitVersion != null && !gitVersion.isBlank()) {
				return new Capabilities(gitVersion, List.of());
			}
		}
		catch (RuntimeException ex) {
			log.debug("Could not read cluster version for capabilities: {}", ex.getMessage());
		}
		return Capabilities.DEFAULT;
	}

	// ---------------------------------------------------------------- helpers

	private GenericKubernetesResource toGeneric(Map<?, ?> resource) {
		// Round-trip through Fabric8's own deserializer (dump to YAML, then unmarshal) so
		// the
		// GenericKubernetesResource's additionalProperties are populated in a form
		// Fabric8
		// can re-serialize for server-side apply. Passing a raw SnakeYAML map straight to
		// convertValue leaves them unserializable (keySerializer NPE on apply).
		return this.client.getKubernetesSerialization().unmarshal(dumpYaml(resource), GenericKubernetesResource.class);
	}

	private static String dumpYaml(Map<?, ?> resource) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		return new Yaml(options).dump(resource);
	}

	private static ObjectMeta metaOf(GenericKubernetesResource gkr) {
		if (gkr.getMetadata() == null) {
			gkr.setMetadata(new ObjectMeta());
		}
		return gkr.getMetadata();
	}

	private static boolean isNamespaced(String kind) {
		return !CLUSTER_SCOPED_KINDS.contains(kind);
	}

	private List<Map<?, ?>> parseResources(String manifest) {
		List<Map<?, ?>> resources = new ArrayList<>();
		for (Object doc : loadUnstructured(manifest)) {
			if (doc instanceof Map<?, ?> map) {
				resources.add(map);
			}
		}
		return resources;
	}

	private static Iterable<Object> loadUnstructured(String yamlContent) {
		Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
		return yaml.loadAll(yamlContent);
	}

	private static String[] identify(Map<?, ?> resource) {
		Object apiVersion = resource.get("apiVersion");
		Object kind = resource.get("kind");
		Object metadata = resource.get("metadata");
		Object name = (metadata instanceof Map<?, ?> meta) ? meta.get("name") : null;
		if (apiVersion == null || kind == null || name == null) {
			throw new KubernetesOperationException("Manifest document missing apiVersion/kind/metadata.name");
		}
		return new String[] { apiVersion.toString(), kind.toString(), name.toString() };
	}

	private static int nz(Integer value) {
		return (value != null) ? value : 0;
	}

	private static long gen(Long generation) {
		return (generation != null) ? generation : 0;
	}

	private static boolean observedCurrent(long generation, Long observed) {
		return ((observed != null) ? observed : 0) >= generation;
	}

	private static ResourceStatus status(String kind, String name, String namespace, boolean ready, String message) {
		return ResourceStatus.builder()
			.kind(kind)
			.name(name)
			.namespace(namespace)
			.ready(ready)
			.message(message)
			.build();
	}

	private static ResourceStatus notFound(String kind, String name, String namespace) {
		return status(kind, name, namespace, false, "not found");
	}

	private static String describe(List<ResourceStatus> statuses) {
		return statuses.stream()
			.map((s) -> s.getKind() + "/" + s.getName() + ": " + s.getMessage())
			.reduce((a, b) -> a + ", " + b)
			.orElse("");
	}

	private static void sleep(long millis, String interruptMessage) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new KubernetesOperationException(interruptMessage, ex);
		}
	}

}
