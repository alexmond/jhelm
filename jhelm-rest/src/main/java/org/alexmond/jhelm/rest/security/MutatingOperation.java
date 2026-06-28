package org.alexmond.jhelm.rest.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller endpoint as a cluster-mutating operation. Such endpoints are gated
 * by the unified {@link org.alexmond.jhelm.core.config.JhelmSecurityPolicy security
 * policy}: rejected with {@code 403 Forbidden} when mutating operations are disabled
 * (deny-by-default — {@code jhelm.security.mode} is not {@code FULL} or no
 * {@code jhelm.security.api-key} is set), and with {@code 401 Unauthorized} when the
 * request does not carry a valid API key.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MutatingOperation {

}
