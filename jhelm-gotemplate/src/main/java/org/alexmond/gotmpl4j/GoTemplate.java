package org.alexmond.gotmpl4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.gotmpl4j.exec.Executor;
import org.alexmond.gotmpl4j.html.Escaper;
import org.alexmond.gotmpl4j.html.Escapers;
import org.alexmond.gotmpl4j.parse.Node;
import org.alexmond.gotmpl4j.parse.Parser;
import org.alexmond.gotmpl4j.util.IOUtils;

/**
 * GoTemplate represents a parsed Go template. It can contain multiple named templates
 * (definitions).
 *
 * <p>
 * Create instances via the default constructor (auto-discovers {@link FunctionProvider}s
 * on classpath) or via the {@link Builder}:
 *
 * <pre>{@code
 * // Auto-discovery (finds SprigFunctionProvider, HelmFunctionProvider, etc.)
 * GoTemplate t = new GoTemplate();
 *
 * // Explicit control
 * GoTemplate t = GoTemplate.builder()
 *     .withProvider(new SprigFunctionProvider())
 *     .withProvider(new HelmFunctionProvider(kubeProvider))
 *     .build();
 * }</pre>
 */
@Slf4j
@Getter
public class GoTemplate {

	private final Map<String, Function> functions;

	private final Map<String, Node> rootNodes;

	private String name;

	private boolean htmlEscape;

	@Getter(AccessLevel.NONE)
	private Escaper escaper;

	@Getter(AccessLevel.NONE)
	private final Set<String> escapedNames = ConcurrentHashMap.newKeySet();

	@Getter(AccessLevel.NONE)
	private final ReentrantLock escapeLock = new ReentrantLock();

	/**
	 * Create a new GoTemplate with default settings. Auto-discovers
	 * {@link FunctionProvider} implementations on the classpath via
	 * {@link java.util.ServiceLoader}.
	 */
	public GoTemplate() {
		this((Map<String, Function>) null);
	}

	/**
	 * Create a new GoTemplate with custom function overrides. Auto-discovers
	 * {@link FunctionProvider} implementations, then applies custom overrides on top.
	 * @param functions custom functions to add on top of discovered providers (may be
	 * {@code null})
	 */
	public GoTemplate(Map<String, Function> functions) {
		this.functions = new LinkedHashMap<>(Functions.GO_BUILTINS);
		this.rootNodes = new LinkedHashMap<>();

		// Discover providers via ServiceLoader, sorted by priority
		List<FunctionProvider> providers = new ArrayList<>();
		for (FunctionProvider discovered : ServiceLoader.load(FunctionProvider.class)) {
			providers.add(discovered);
		}
		providers.sort(Comparator.comparingInt(FunctionProvider::priority));

		// Apply providers in priority order, passing this (the actual template)
		for (FunctionProvider provider : providers) {
			this.functions.putAll(provider.getFunctions(this));
		}

		// Custom overrides on top
		if (functions != null) {
			this.functions.putAll(functions);
		}
	}

	/**
	 * Internal constructor used by {@link Builder}.
	 */
	GoTemplate(Map<String, Function> functions, boolean fromBuilder) {
		this.functions = new LinkedHashMap<>(functions);
		this.rootNodes = new LinkedHashMap<>();
	}

	/**
	 * Create a new {@link Builder} for fine-grained control over function providers.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Parse an unnamed template.
	 * @param text Template text
	 */
	public GoTemplate parse(String text) throws TemplateParseException {
		return parse("", text);
	}

	/**
	 * Parse a named template.
	 * @param name The template name
	 * @param text Template text
	 */
	public GoTemplate parse(String name, String text) throws TemplateParseException {
		if (this.name == null || this.name.isEmpty()) {
			this.name = name;
		}
		Parser parser = new Parser(functions);
		try {
			Map<String, Node> parsedNodes = parser.parse(name, text);
			rootNodes.putAll(parsedNodes);
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Parse error in {}: {}. Text: {}", name, ex.getMessage(),
						(text.length() > 100) ? text.substring(0, 100) + "..." : text);
			}
			throw new TemplateParseException("Internal error during parsing of " + name, ex);
		}
		return this;
	}

	/**
	 * Parse a named template from InputStream
	 */
	public GoTemplate parse(String name, InputStream in) throws TemplateParseException, IOException {
		return parse(name, new InputStreamReader(in));
	}

	/**
	 * Parse a named template from Reader
	 */
	public GoTemplate parse(String name, Reader reader) throws TemplateParseException, IOException {
		String text = IOUtils.read(reader);
		return parse(name, text);
	}

	/**
	 * Execute the main template
	 */
	public void execute(Object data, Writer writer)
			throws IOException, TemplateNotFoundException, TemplateExecutionException {
		execute(name, data, writer);
	}

	/**
	 * Execute a named template from this template set
	 */
	public void execute(String name, Object data, Writer writer)
			throws IOException, TemplateNotFoundException, TemplateExecutionException {
		if (name == null || !rootNodes.containsKey(name)) {
			throw new TemplateNotFoundException(String.format("Template '%s' not found.", name));
		}
		if (htmlEscape) {
			ensureEscaped(name);
		}
		Executor executor = new Executor(rootNodes, functions);
		executor.execute(name, data, writer);
	}

	/**
	 * Enables opt-in {@code html/template} contextual auto-escaping: the named template
	 * is rewritten (lazily, on first execution) so interpolated values are escaped for
	 * the HTML/JS/CSS/URL context they appear in, and the internal escaper functions are
	 * registered. The default mode is {@code text/template} (no escaping), matching Helm.
	 */
	void enableHtmlEscaping() {
		this.htmlEscape = true;
		this.functions.putAll(Escapers.escapers());
		this.escaper = new Escaper(this.rootNodes);
	}

	private void ensureEscaped(String name) {
		if (escapedNames.contains(name)) {
			return;
		}
		escapeLock.lock();
		try {
			if (!escapedNames.contains(name)) {
				escaper.escapeOne(name);
				escapedNames.add(name);
			}
		}
		finally {
			escapeLock.unlock();
		}
	}

	/**
	 * Execute the main template and return the result as a {@code String}. Convenience
	 * over {@link #execute(Object, Writer)} for callers that want a string rather than
	 * supplying a {@link Writer}.
	 * @param data the root data object (the template's {@code .})
	 * @return the rendered output
	 * @throws TemplateNotFoundException if no main template has been parsed
	 * @throws TemplateExecutionException if execution fails
	 */
	public String render(Object data) throws TemplateNotFoundException, TemplateExecutionException {
		return render(name, data);
	}

	/**
	 * Execute a named template from this set and return the result as a {@code String}.
	 * Convenience over {@link #execute(String, Object, Writer)}.
	 * @param name the template name
	 * @param data the root data object (the template's {@code .})
	 * @return the rendered output
	 * @throws TemplateNotFoundException if no template with the given name exists
	 * @throws TemplateExecutionException if execution fails
	 */
	public String render(String name, Object data) throws TemplateNotFoundException, TemplateExecutionException {
		StringWriter writer = new StringWriter();
		try {
			execute(name, data, writer);
		}
		catch (IOException ex) {
			// A StringWriter never performs I/O; an IOException here is not expected.
			throw new TemplateExecutionException("Unexpected I/O error rendering '" + name + "'", ex);
		}
		return writer.toString();
	}

	/**
	 * Return an immutable handle to a named template in this set, bound once so it can be
	 * rendered repeatedly without repeating the name.
	 * @param name the template name
	 * @return a handle bound to the named template
	 * @throws TemplateNotFoundException if no template with the given name exists
	 */
	public CompiledTemplate compiled(String name) throws TemplateNotFoundException {
		if (name == null || !rootNodes.containsKey(name)) {
			throw new TemplateNotFoundException(String.format("Template '%s' not found.", name));
		}
		return new CompiledTemplate(this, name);
	}

	/**
	 * Check if a named template exists.
	 * @param name the template name to check
	 * @return {@code true} if a template with the given name exists, {@code false}
	 * otherwise
	 */
	public boolean hasTemplate(String name) {
		return rootNodes.containsKey(name);
	}

	/**
	 * Get the root node of the main template
	 */
	public Node root() {
		return rootNodes.get(name);
	}

	/**
	 * Get the root node of a named template
	 */
	public Node root(String name) {
		return rootNodes.get(name);
	}

	/**
	 * Builder for constructing {@link GoTemplate} instances with explicit control over
	 * function providers. Supports ServiceLoader auto-discovery and/or explicit provider
	 * registration.
	 */
	public static class Builder {

		private final List<FunctionProvider> providers = new ArrayList<>();

		private Map<String, Function> extraFunctions;

		private boolean autoDiscovery = true;

		private boolean htmlEscaping;

		Builder() {
		}

		/**
		 * Enable opt-in {@code html/template} contextual auto-escaping. The default is
		 * {@code text/template} (no escaping), which is what Helm uses.
		 * @return this builder
		 */
		public Builder htmlEscaping() {
			this.htmlEscaping = true;
			return this;
		}

		/**
		 * Add an explicit function provider.
		 * @param provider the provider to add
		 * @return this builder
		 */
		public Builder withProvider(FunctionProvider provider) {
			this.providers.add(provider);
			return this;
		}

		/**
		 * Add raw functions that override all providers.
		 * @param functions the functions to add
		 * @return this builder
		 */
		public Builder withFunctions(Map<String, Function> functions) {
			this.extraFunctions = functions;
			return this;
		}

		/**
		 * Disable ServiceLoader auto-discovery. Only explicitly added providers and
		 * functions will be used.
		 * @return this builder
		 */
		public Builder noAutoDiscovery() {
			this.autoDiscovery = false;
			return this;
		}

		/**
		 * Build the {@link GoTemplate} instance.
		 * @return a new GoTemplate with configured functions
		 */
		public GoTemplate build() {
			// Start with Go builtins
			LinkedHashMap<String, Function> allFunctions = new LinkedHashMap<>(Functions.GO_BUILTINS);

			// Collect all providers: auto-discovered + explicit
			List<FunctionProvider> allProviders = new ArrayList<>();
			if (autoDiscovery) {
				ServiceLoader<FunctionProvider> loader = ServiceLoader.load(FunctionProvider.class);
				for (FunctionProvider discovered : loader) {
					if (log.isDebugEnabled()) {
						log.debug("Discovered FunctionProvider: {} (priority={})", discovered.name(),
								discovered.priority());
					}
					allProviders.add(discovered);
				}
			}
			allProviders.addAll(providers);

			// Sort by priority (lower first, higher overrides)
			allProviders.sort(Comparator.comparingInt(FunctionProvider::priority));

			// Build the template first (providers may need the factory ref)
			GoTemplate template = new GoTemplate(allFunctions, true);

			// Apply providers in priority order
			for (FunctionProvider provider : allProviders) {
				Map<String, Function> providerFunctions = provider.getFunctions(template);
				template.functions.putAll(providerFunctions);
				if (log.isDebugEnabled()) {
					log.debug("Loaded {} functions from {} (priority={})", providerFunctions.size(), provider.name(),
							provider.priority());
				}
			}

			// Raw functions override everything
			if (extraFunctions != null) {
				template.functions.putAll(extraFunctions);
			}

			if (htmlEscaping) {
				template.enableHtmlEscaping();
			}

			return template;
		}

	}

}
