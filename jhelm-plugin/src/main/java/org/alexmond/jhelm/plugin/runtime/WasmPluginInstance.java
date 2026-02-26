package org.alexmond.jhelm.plugin.runtime;

import com.dylibso.chicory.runtime.Instance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Wrapper around a loaded Chicory WASM {@link Instance}.
 */
@Getter
@RequiredArgsConstructor
public class WasmPluginInstance implements AutoCloseable {

	private final String name;

	private final Instance instance;

	/**
	 * Call a WASM exported function with the given arguments.
	 * @param functionName the export name
	 * @param args the function arguments
	 * @return the result values
	 */
	public long[] call(String functionName, long... args) {
		return instance.export(functionName).apply(args);
	}

	/**
	 * Check whether the WASM module exports a function with the given name.
	 * @param functionName the export name
	 * @return {@code true} if the export exists
	 */
	public boolean hasExport(String functionName) {
		try {
			instance.export(functionName);
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}

	@Override
	public void close() {
		// Chicory instances do not require explicit cleanup
	}

}
