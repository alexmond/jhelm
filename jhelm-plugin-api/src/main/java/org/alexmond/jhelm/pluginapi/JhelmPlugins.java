package org.alexmond.jhelm.pluginapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers jhelm Java plugins off the classpath. Plugins may be published as JDK
 * {@link java.util.ServiceLoader} services (a {@code META-INF/services/<interface>} file)
 * or, in a Spring application, supplied as beans; {@link #merge(Class, Collection)}
 * unions both and de-duplicates by implementation class (preferring the supplied bean
 * instance, which may carry injected dependencies).
 */
public final class JhelmPlugins {

	private static final Logger log = LoggerFactory.getLogger(JhelmPlugins.class);

	private JhelmPlugins() {
	}

	/**
	 * Loads every plugin of the given type published as a {@code ServiceLoader} service.
	 * @param type the plugin interface to load
	 * @param <T> the plugin type
	 * @return the discovered service instances (empty if none)
	 */
	public static <T extends JhelmPlugin> List<T> fromServiceLoader(Class<T> type) {
		List<T> found = new ArrayList<>();
		for (T plugin : ServiceLoader.load(type)) {
			found.add(plugin);
		}
		if (!found.isEmpty() && log.isDebugEnabled()) {
			log.debug("Discovered {} {} plugin(s) via ServiceLoader", found.size(), type.getSimpleName());
		}
		return found;
	}

	/**
	 * Unions the supplied plugins (for example Spring beans) with the
	 * {@code ServiceLoader} services of the same type, de-duplicated by implementation
	 * class so a plugin that is both a bean and a declared service is not registered
	 * twice. Supplied instances win.
	 * @param type the plugin interface
	 * @param supplied plugins provided by the caller (may be {@code null} or empty)
	 * @param <T> the plugin type
	 * @return the merged, de-duplicated plugin list
	 */
	public static <T extends JhelmPlugin> List<T> merge(Class<T> type, Collection<? extends T> supplied) {
		List<T> merged = new ArrayList<>();
		Set<Class<?>> seen = new HashSet<>();
		if (supplied != null) {
			for (T plugin : supplied) {
				if (seen.add(plugin.getClass())) {
					merged.add(plugin);
				}
			}
		}
		for (T plugin : fromServiceLoader(type)) {
			if (seen.add(plugin.getClass())) {
				merged.add(plugin);
			}
		}
		return merged;
	}

}
