package org.alexmond.jhelm.pluginapi;

/**
 * Base type for every jhelm Java plugin. A plugin is an ordinary Java object implementing
 * one of the extension interfaces ({@link JhelmPostRenderer},
 * {@link JhelmChartDownloader}, {@link JhelmLifecycleListener},
 * {@link JhelmTemplateFunctionProvider}); jhelm discovers it via the JDK
 * {@link java.util.ServiceLoader} or, in a Spring application, as a bean.
 *
 * @see JhelmPlugins
 */
public interface JhelmPlugin {

	/**
	 * A short, stable name used to identify the plugin in listings and logs. Defaults to
	 * the implementing class's simple name.
	 * @return the plugin name
	 */
	default String name() {
		return getClass().getSimpleName();
	}

}
