package org.alexmond.jhelm.kube.service;

import java.util.List;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import java.net.ConnectException;
import java.net.SocketException;

/**
 * A {@link KubeService} decorator that retries transient Kubernetes API failures using a
 * {@link RetryTemplate}. Transient errors include 5xx server errors, 429 rate-limiting
 * responses, and connection-level exceptions.
 * <p>
 * Only operations that hit the Kubernetes API are retried. Non-transient errors (4xx
 * client errors) are propagated immediately.
 * </p>
 */
@Slf4j
public class RetryableKubeService implements KubeService {

	private final KubeService delegate;

	private final RetryTemplate retryTemplate;

	public RetryableKubeService(KubeService delegate, RetryTemplate retryTemplate) {
		this.delegate = delegate;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public void storeRelease(Release release) throws Exception {
		executeWithRetry("storeRelease", (ctx) -> {
			delegate.storeRelease(release);
			return null;
		});
	}

	@Override
	public Optional<Release> getRelease(String name, String namespace) throws Exception {
		return executeWithRetry("getRelease", (ctx) -> delegate.getRelease(name, namespace));
	}

	@Override
	public List<Release> listReleases(String namespace) throws Exception {
		return executeWithRetry("listReleases", (ctx) -> delegate.listReleases(namespace));
	}

	@Override
	public List<Release> getReleaseHistory(String name, String namespace) throws Exception {
		return executeWithRetry("getReleaseHistory", (ctx) -> delegate.getReleaseHistory(name, namespace));
	}

	@Override
	public void deleteReleaseHistory(String name, String namespace) throws Exception {
		executeWithRetry("deleteReleaseHistory", (ctx) -> {
			delegate.deleteReleaseHistory(name, namespace);
			return null;
		});
	}

	@Override
	public void apply(String namespace, String yamlContent) throws Exception {
		executeWithRetry("apply", (ctx) -> {
			delegate.apply(namespace, yamlContent);
			return null;
		});
	}

	@Override
	public void delete(String namespace, String yamlContent) throws Exception {
		executeWithRetry("delete", (ctx) -> {
			delegate.delete(namespace, yamlContent);
			return null;
		});
	}

	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) throws Exception {
		return executeWithRetry("getResourceStatuses", (ctx) -> delegate.getResourceStatuses(namespace, manifest));
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) throws Exception {
		// waitForReady already polls internally; do not retry the entire operation
		delegate.waitForReady(namespace, manifest, timeoutSeconds);
	}

	private <T> T executeWithRetry(String operation, RetryCallback<T, Exception> callback) throws Exception {
		return retryTemplate.execute(callback, (ctx) -> {
			Throwable last = ctx.getLastThrowable();
			if (log.isErrorEnabled()) {
				log.error("All retry attempts exhausted for {}", operation, last);
			}
			if (last instanceof Exception ex) {
				throw ex;
			}
			throw new KubernetesOperationException("Retry exhausted for " + operation, last);
		});
	}

	/**
	 * Returns {@code true} if the given exception represents a transient Kubernetes API
	 * error that may succeed on retry.
	 */
	public static boolean isTransient(Throwable ex) {
		if (ex instanceof ApiException apiEx) {
			int code = apiEx.getCode();
			return code == 429 || (code >= 500 && code < 600);
		}
		if (ex instanceof KubernetesOperationException kubeEx) {
			return kubeEx.isTransient();
		}
		// Connection-level errors (SocketException, ConnectException, etc.)
		if (ex instanceof SocketException || ex instanceof ConnectException) {
			return true;
		}
		// Check wrapped cause
		if (ex.getCause() != null && !ex.getCause().equals(ex)) {
			return isTransient(ex.getCause());
		}
		return false;
	}

}
