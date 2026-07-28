package org.alexmond.jhelm.kube;

import java.util.Locale;
import java.util.Map;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Decides which Kubernetes backend configuration is active, so that exactly one backend
 * wins regardless of import order. Reads {@code jhelm.kubernetes.backend}:
 * <ul>
 * <li>an explicit backend id ({@code client-java} / {@code fabric8}) activates only that
 * backend, and only when its client library is on the classpath;</li>
 * <li>{@code auto} (the default) activates the first backend, in {@link KubeBackend}
 * declaration order, whose client library is present — so {@code client-java} is
 * preferred over Fabric8 when both are available;</li>
 * <li>{@code none} activates no backend at all, so jhelm-kube builds no ambient
 * {@code KubernetesClient} and no default {@code KubeService} /
 * {@code KubernetesProvider} even when a client library is on the classpath — the host
 * supplies its own {@code KubeService}(s).</li>
 * </ul>
 */
public class OnKubeBackendCondition implements Condition {

	private static final String PROPERTY = "jhelm.kubernetes.backend";

	private static final String AUTO = "auto";

	private static final String NONE = "none";

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		KubeBackend target = targetBackend(metadata);
		String configured = context.getEnvironment().getProperty(PROPERTY, AUTO).trim().toLowerCase(Locale.ROOT);
		if (NONE.equals(configured)) {
			// No ambient backend: the host brings its own KubeService(s). Suppress every
			// backend-gated bean regardless of what client libraries are present.
			return false;
		}
		ClassLoader classLoader = context.getClassLoader();
		if (!AUTO.equals(configured)) {
			// Explicit selection: only the named backend, and only if its library is
			// here.
			return target.id().equals(configured) && isPresent(target, classLoader);
		}
		// Auto: the highest-preference backend that is actually on the classpath wins.
		for (KubeBackend candidate : KubeBackend.values()) {
			if (isPresent(candidate, classLoader)) {
				return candidate == target;
			}
		}
		return false;
	}

	private static KubeBackend targetBackend(AnnotatedTypeMetadata metadata) {
		Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnKubeBackend.class.getName());
		if (attributes == null) {
			throw new IllegalStateException(
					"@ConditionalOnKubeBackend is required to use " + OnKubeBackendCondition.class.getSimpleName());
		}
		return (KubeBackend) attributes.get("value");
	}

	private static boolean isPresent(KubeBackend backend, ClassLoader classLoader) {
		return ClassUtils.isPresent(backend.markerClass(), classLoader);
	}

}
