package org.alexmond.jhelm.kube;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/**
 * Marks a Kubernetes backend configuration as active only when the annotated
 * {@link KubeBackend} is the one jhelm selects. Selection is driven by the
 * {@code jhelm.kubernetes.backend} property and the client libraries on the classpath
 * (see {@link OnKubeBackendCondition}), guaranteeing that <em>exactly one</em> backend
 * activates even when both client libraries are present — independent of configuration
 * import order.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnKubeBackendCondition.class)
public @interface ConditionalOnKubeBackend {

	/**
	 * The backend this configuration provides.
	 * @return the backend guarded by this condition
	 */
	KubeBackend value();

}
