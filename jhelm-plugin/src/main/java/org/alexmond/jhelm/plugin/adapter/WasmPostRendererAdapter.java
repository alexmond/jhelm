package org.alexmond.jhelm.plugin.adapter;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.plugin.exception.PluginExecutionException;
import org.alexmond.jhelm.plugin.model.PluginType;
import org.alexmond.jhelm.plugin.runtime.MemoryBridge;
import org.alexmond.jhelm.plugin.runtime.WasmPluginInstance;
import org.alexmond.jhelm.plugin.sandbox.SandboxConfig;
import org.alexmond.jhelm.plugin.sandbox.SandboxedExecutor;
import org.alexmond.jhelm.plugin.spi.PostRendererPlugin;

/**
 * Adapts a WASM module that exports {@code post_render} to the {@link PostRendererPlugin}
 * interface.
 */
@RequiredArgsConstructor
public class WasmPostRendererAdapter implements PostRendererPlugin {

	private final String pluginName;

	private final WasmPluginInstance wasmInstance;

	private final SandboxedExecutor sandboxedExecutor;

	private final SandboxConfig sandboxConfig;

	@Override
	public String postRender(String renderedManifest) throws PluginExecutionException {
		try {
			return sandboxedExecutor.execute(pluginName, sandboxConfig, () -> {
				long packed = MemoryBridge.writeString(wasmInstance.getInstance(), renderedManifest);
				int ptr = MemoryBridge.unpackPtr(packed);
				int len = MemoryBridge.unpackLen(packed);
				long[] result = wasmInstance.call("post_render", ptr, len);
				long resultPacked = result[0];
				int resultPtr = MemoryBridge.unpackPtr(resultPacked);
				int resultLen = MemoryBridge.unpackLen(resultPacked);
				return MemoryBridge.readString(wasmInstance.getInstance(), resultPtr, resultLen);
			});
		}
		catch (PluginExecutionException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new PluginExecutionException("Post-render plugin '" + pluginName + "' failed", ex);
		}
	}

	@Override
	public String name() {
		return pluginName;
	}

	@Override
	public PluginType type() {
		return PluginType.POST_RENDERER;
	}

	@Override
	public void close() {
		wasmInstance.close();
	}

}
