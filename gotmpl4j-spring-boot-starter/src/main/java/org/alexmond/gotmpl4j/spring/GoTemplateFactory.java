package org.alexmond.gotmpl4j.spring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.FunctionProvider;
import org.alexmond.gotmpl4j.GoTemplate;

/**
 * Builds configured {@link GoTemplate} instances, bridging Spring-managed function
 * extensions into the engine. This is the Spring-idiomatic extension point: any
 * {@link FunctionProvider} bean in the context is registered with the engine (on top of
 * the classpath providers the engine discovers via {@code ServiceLoader}, e.g. sprig and
 * helm), and any extra named {@link Function}s override on top.
 *
 * <p>
 * It mirrors how other Spring template engines expose extension — Thymeleaf collects
 * {@code IDialect} beans and registers them on the engine — rather than maintaining a
 * parallel function registry in the starter: the engine owns the SPI, the starter only
 * feeds Spring beans into it.
 *
 * <p>
 * Every call to {@link #create()} returns a fresh, empty template set ready to be parsed
 * into; the configured functions are identical across instances, so a caller may compile
 * once and reuse (see {@link org.alexmond.gotmpl4j.TemplateCache}).
 */
public class GoTemplateFactory {

	private final List<FunctionProvider> providers;

	private final Map<String, Function> extraFunctions;

	/** Creates a factory with no Spring-provided extensions (engine defaults only). */
	public GoTemplateFactory() {
		this(List.of(), Map.of());
	}

	/**
	 * Creates a factory.
	 * @param providers function providers contributed by the Spring context (applied on
	 * top of {@code ServiceLoader}-discovered providers, by priority)
	 * @param extraFunctions named functions that override all providers (may be empty)
	 */
	public GoTemplateFactory(List<FunctionProvider> providers, Map<String, Function> extraFunctions) {
		this.providers = new ArrayList<>(providers);
		this.extraFunctions = new LinkedHashMap<>(extraFunctions);
	}

	/**
	 * Creates a fresh {@link GoTemplate} configured with the engine defaults plus the
	 * Spring-provided providers and functions.
	 * @return a new, empty configured template set
	 */
	public GoTemplate create() {
		GoTemplate.Builder builder = GoTemplate.builder();
		for (FunctionProvider provider : this.providers) {
			builder.withProvider(provider);
		}
		if (!this.extraFunctions.isEmpty()) {
			builder.withFunctions(this.extraFunctions);
		}
		return builder.build();
	}

}
