package org.alexmond.jhelm.gotemplate.sprig;

import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.FunctionProvider;
import org.alexmond.jhelm.gotemplate.GoTemplate;

/**
 * {@link FunctionProvider} that contributes all Sprig template functions.
 *
 * <p>
 * Discovered automatically via {@link java.util.ServiceLoader} when
 * {@code jhelm-gotemplate-sprig} is on the classpath. Can also be registered explicitly
 * via {@link GoTemplate.Builder#withProvider(FunctionProvider)}.
 *
 * <p>
 * Priority is {@code 100} (between Go builtins at 0 and Helm functions at 200).
 *
 * @see SprigFunctionsRegistry
 */
public class SprigFunctionProvider implements FunctionProvider {

	@Override
	public Map<String, Function> getFunctions(GoTemplate template) {
		return SprigFunctionsRegistry.getAllFunctions();
	}

	@Override
	public int priority() {
		return 100;
	}

	@Override
	public String name() {
		return "Sprig";
	}

}
