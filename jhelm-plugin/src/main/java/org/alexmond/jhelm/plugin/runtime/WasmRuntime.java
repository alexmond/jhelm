package org.alexmond.jhelm.plugin.runtime;

import java.util.List;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.plugin.exception.PluginLoadException;

/**
 * Chicory-based WASM runtime. Creates {@link WasmPluginInstance} instances from WASM
 * binary content.
 */
@Slf4j
@RequiredArgsConstructor
public class WasmRuntime {

	private final HostFunctionBridge hostFunctionBridge;

	/**
	 * Load a WASM module from raw bytes and create a plugin instance.
	 * @param pluginName the plugin name
	 * @param wasmBytes the raw WASM binary
	 * @return the loaded plugin instance
	 * @throws PluginLoadException if loading fails
	 */
	public WasmPluginInstance load(String pluginName, byte[] wasmBytes) throws PluginLoadException {
		try {
			WasmModule module = Parser.parse(wasmBytes);
			List<HostFunction> hostFunctions = hostFunctionBridge.createHostFunctions();
			Store store = new Store();
			for (HostFunction hf : hostFunctions) {
				store.addFunction(hf);
			}
			Instance instance = store.instantiate(pluginName, module);
			if (log.isInfoEnabled()) {
				log.info("Loaded WASM plugin: {}", pluginName);
			}
			return new WasmPluginInstance(pluginName, instance);
		}
		catch (Exception ex) {
			throw new PluginLoadException("Failed to load WASM plugin: " + pluginName, ex);
		}
	}

}
