package org.alexmond.jhelm.plugin.adapter;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.plugin.exception.PluginExecutionException;
import org.alexmond.jhelm.plugin.model.PluginType;
import org.alexmond.jhelm.plugin.runtime.MemoryBridge;
import org.alexmond.jhelm.plugin.runtime.WasmPluginInstance;
import org.alexmond.jhelm.plugin.sandbox.SandboxConfig;
import org.alexmond.jhelm.plugin.sandbox.SandboxedExecutor;
import org.alexmond.jhelm.plugin.spi.DownloaderPlugin;

/**
 * Adapts a WASM module that exports {@code download} and {@code supports_protocol} to the
 * {@link DownloaderPlugin} interface.
 */
@RequiredArgsConstructor
public class WasmDownloaderAdapter implements DownloaderPlugin {

	private final String pluginName;

	private final WasmPluginInstance wasmInstance;

	private final SandboxedExecutor sandboxedExecutor;

	private final SandboxConfig sandboxConfig;

	@Override
	public boolean supportsProtocol(String protocol) {
		try {
			long packed = MemoryBridge.writeString(wasmInstance.getInstance(), protocol);
			int ptr = MemoryBridge.unpackPtr(packed);
			int len = MemoryBridge.unpackLen(packed);
			long[] result = wasmInstance.call("supports_protocol", ptr, len);
			return result[0] == 1;
		}
		catch (Exception ex) {
			return false;
		}
	}

	@Override
	public byte[] download(String url) throws PluginExecutionException {
		try {
			return sandboxedExecutor.execute(pluginName, sandboxConfig, () -> {
				long packed = MemoryBridge.writeString(wasmInstance.getInstance(), url);
				int ptr = MemoryBridge.unpackPtr(packed);
				int len = MemoryBridge.unpackLen(packed);
				long[] result = wasmInstance.call("download", ptr, len);
				long resultPacked = result[0];
				int resultPtr = MemoryBridge.unpackPtr(resultPacked);
				int resultLen = MemoryBridge.unpackLen(resultPacked);
				return MemoryBridge.readBytes(wasmInstance.getInstance(), resultPtr, resultLen);
			});
		}
		catch (PluginExecutionException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new PluginExecutionException("Downloader plugin '" + pluginName + "' failed", ex);
		}
	}

	@Override
	public String name() {
		return pluginName;
	}

	@Override
	public PluginType type() {
		return PluginType.DOWNLOADER;
	}

	@Override
	public void close() {
		wasmInstance.close();
	}

}
