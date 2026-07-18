package org.alexmond.jhelm.app.plugin;

import java.util.List;
import java.util.Optional;

import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * Bridges an unknown top-level subcommand to a Helm plugin. When Picocli cannot match a
 * subcommand on the root {@code jhelm} command, this handler checks whether the unmatched
 * name is an installed Helm plugin; if so it runs the plugin with the remaining arguments
 * (as {@code helm} does), otherwise it defers to the default handler so the normal
 * "Unmatched argument" error is shown.
 */
public class HelmPluginParameterExceptionHandler implements IParameterExceptionHandler {

	private final IParameterExceptionHandler delegate;

	private final HelmPluginDispatcher dispatcher;

	/**
	 * Creates the handler.
	 * @param delegate the handler to fall back to when the unmatched name is not a plugin
	 * @param dispatcher resolves and runs installed Helm plugins
	 */
	public HelmPluginParameterExceptionHandler(IParameterExceptionHandler delegate, HelmPluginDispatcher dispatcher) {
		this.delegate = delegate;
		this.dispatcher = dispatcher;
	}

	@Override
	public int handleParseException(ParameterException ex, String[] args) throws Exception {
		if (ex instanceof UnmatchedArgumentException unmatchedEx && ex.getCommandLine().getParent() == null) {
			List<String> unmatched = unmatchedEx.getUnmatched();
			if (!unmatched.isEmpty()) {
				String name = unmatched.get(0);
				Optional<DiscoveredHelmPlugin> plugin = this.dispatcher.find(name);
				if (plugin.isPresent()) {
					return this.dispatcher.dispatch(plugin.get(), unmatched.subList(1, unmatched.size()));
				}
			}
		}
		return this.delegate.handleParseException(ex, args);
	}

}
