package org.alexmond.jhelm.plugin.adapter;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.plugin.exception.PluginExecutionException;
import org.alexmond.jhelm.plugin.model.PluginEvent;
import org.alexmond.jhelm.plugin.model.PluginType;
import org.alexmond.jhelm.plugin.runtime.MemoryBridge;
import org.alexmond.jhelm.plugin.runtime.WasmPluginInstance;
import org.alexmond.jhelm.plugin.sandbox.SandboxConfig;
import org.alexmond.jhelm.plugin.sandbox.SandboxedExecutor;
import org.alexmond.jhelm.plugin.spi.LifecycleHookPlugin;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapts a WASM module that exports {@code on_event} to the {@link LifecycleHookPlugin}
 * interface.
 */
@RequiredArgsConstructor
public class WasmLifecycleHookAdapter implements LifecycleHookPlugin {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private final String pluginName;

	private final WasmPluginInstance wasmInstance;

	private final SandboxedExecutor sandboxedExecutor;

	private final SandboxConfig sandboxConfig;

	@Override
	public void onEvent(PluginEvent event) throws PluginExecutionException {
		try {
			sandboxedExecutor.execute(pluginName, sandboxConfig, () -> {
				String json = JSON_MAPPER.writeValueAsString(event);
				long packed = MemoryBridge.writeString(wasmInstance.getInstance(), json);
				int ptr = MemoryBridge.unpackPtr(packed);
				int len = MemoryBridge.unpackLen(packed);
				long[] result = wasmInstance.call("on_event", ptr, len);
				if (result[0] != 0) {
					throw new PluginExecutionException(
							"Lifecycle hook plugin '" + pluginName + "' returned error code: " + result[0]);
				}
				return null;
			});
		}
		catch (PluginExecutionException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new PluginExecutionException("Lifecycle hook plugin '" + pluginName + "' failed", ex);
		}
	}

	@Override
	public String name() {
		return pluginName;
	}

	@Override
	public PluginType type() {
		return PluginType.LIFECYCLE_HOOK;
	}

	@Override
	public void close() {
		wasmInstance.close();
	}

}
