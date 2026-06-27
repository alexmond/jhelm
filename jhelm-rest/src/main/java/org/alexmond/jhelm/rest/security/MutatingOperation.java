package org.alexmond.jhelm.rest.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller endpoint as a cluster-mutating operation. Such endpoints are
 * rejected with {@code 403 Forbidden} when {@code jhelm.rest.mode} is
 * {@link org.alexmond.jhelm.core.config.JhelmAccessMode#READ_ONLY READ_ONLY}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MutatingOperation {

}
