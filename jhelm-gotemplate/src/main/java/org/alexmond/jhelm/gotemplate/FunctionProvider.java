package org.alexmond.jhelm.gotemplate;

import java.util.Map;

/**
 * Service Provider Interface for registering template functions with {@link GoTemplate}.
 * Implementations can be discovered automatically via {@link java.util.ServiceLoader} or
 * registered explicitly via {@link GoTemplate.Builder#withProvider(FunctionProvider)}.
 *
 * <p>
 * Priority ordering controls override behavior when multiple providers register functions
 * with the same name. Higher priority wins.
 *
 * <p>
 * Built-in priorities: Go builtins=0, Sprig=100, Helm=200.
 *
 * @see GoTemplate.Builder
 */
@FunctionalInterface
public interface FunctionProvider {

	/**
	 * Return the functions this provider contributes.
	 * @param template the GoTemplate being constructed (needed by functions like
	 * {@code include} and {@code tpl} that execute templates)
	 * @return map of function name to implementation
	 */
	Map<String, Function> getFunctions(GoTemplate template);

	/**
	 * Priority for ordering and override resolution. Lower values are loaded first;
	 * higher values override on name collision.
	 * @return the priority (default 0)
	 */
	default int priority() {
		return 0;
	}

	/**
	 * Human-readable name for diagnostics and logging.
	 * @return the provider name
	 */
	default String name() {
		return getClass().getSimpleName();
	}

}
