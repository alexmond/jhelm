package org.alexmond.jhelm.kube.service;

import java.util.List;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
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

	/**
	 * Creates a retrying decorator around the given delegate.
	 * @param delegate the underlying {@link KubeService} to which calls are forwarded
	 * @param retryTemplate the retry template defining the backoff and retry policy for
	 * transient failures
	 */
	public RetryableKubeService(KubeService delegate, RetryTemplate retryTemplate) {
		this.delegate = delegate;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public void storeRelease(Release release) {
		executeWithRetry("storeRelease", () -> {
			delegate.storeRelease(release);
			return null;
		});
	}

	@Override
	public Optional<Release> getRelease(String name, String namespace) {
		return executeWithRetry("getRelease", () -> delegate.getRelease(name, namespace));
	}

	@Override
	public List<Release> listReleases(String namespace) {
		return executeWithRetry("listReleases", () -> delegate.listReleases(namespace));
	}

	@Override
	public List<Release> getReleaseHistory(String name, String namespace) {
		return executeWithRetry("getReleaseHistory", () -> delegate.getReleaseHistory(name, namespace));
	}

	@Override
	public void deleteReleaseHistory(String name, String namespace) {
		executeWithRetry("deleteReleaseHistory", () -> {
			delegate.deleteReleaseHistory(name, namespace);
			return null;
		});
	}

	@Override
	public void pruneReleaseHistory(String name, String namespace, int maxHistory) {
		executeWithRetry("pruneReleaseHistory", () -> {
			delegate.pruneReleaseHistory(name, namespace, maxHistory);
			return null;
		});
	}

	@Override
	public void apply(String namespace, String yamlContent) {
		executeWithRetry("apply", () -> {
			delegate.apply(namespace, yamlContent);
			return null;
		});
	}

	@Override
	public void delete(String namespace, String yamlContent) {
		executeWithRetry("delete", () -> {
			delegate.delete(namespace, yamlContent);
			return null;
		});
	}

	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) {
		return executeWithRetry("getResourceStatuses", () -> delegate.getResourceStatuses(namespace, manifest));
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) {
		// waitForReady already polls internally; do not retry the entire operation
		delegate.waitForReady(namespace, manifest, timeoutSeconds);
	}

	private <T> T executeWithRetry(String operation, Retryable<T> callback) {
		try {
			return retryTemplate.execute(callback);
		}
		catch (RetryException ex) {
			// Thrown when the policy stops retrying — either a non-transient failure (the
			// predicate rejected it) or all retries exhausted. The cause is the last
			// underlying failure; rethrow it as-is (every jhelm exception is unchecked)
			// so
			// callers see the original exception.
			Throwable last = ex.getCause();
			if (log.isErrorEnabled()) {
				log.error("All retry attempts exhausted for {}", operation, last);
			}
			if (last instanceof RuntimeException cause) {
				throw cause;
			}
			throw new KubernetesOperationException("Retry exhausted for " + operation, last);
		}
	}

	/**
	 * Determines whether the given exception represents a transient Kubernetes API error
	 * that may succeed on retry. Transient errors include HTTP 429 rate-limiting
	 * responses, 5xx server errors, connection-level failures
	 * ({@link SocketException}/{@link ConnectException}), and any wrapped cause that is
	 * itself transient.
	 * @param ex the exception to classify; its wrapped {@link Throwable#getCause() cause}
	 * is inspected when the exception itself is not transient
	 * @return {@code true} if the error is transient and the operation may be retried,
	 * {@code false} otherwise
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
