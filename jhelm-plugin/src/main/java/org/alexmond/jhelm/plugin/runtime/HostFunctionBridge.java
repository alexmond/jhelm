package org.alexmond.jhelm.plugin.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates host functions in the {@code "jhelm"} namespace that are provided to every WASM
 * plugin.
 */
@Slf4j
public class HostFunctionBridge {

	private static final String NAMESPACE = "jhelm";

	/**
	 * Create the standard set of host functions.
	 * @return the list of host functions
	 */
	public List<HostFunction> createHostFunctions() {
		List<HostFunction> functions = new ArrayList<>();
		functions.add(createLogFunction());
		return functions;
	}

	private HostFunction createLogFunction() {
		return new HostFunction(NAMESPACE, "log",
				FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32), List.of()), (instance, args) -> {
					int level = (int) args[0];
					int ptr = (int) args[1];
					int len = (int) args[2];
					String message = new String(instance.memory().readBytes(ptr, len), StandardCharsets.UTF_8);
					switch (level) {
						case 0 -> log.trace("[plugin] {}", message);
						case 1 -> log.debug("[plugin] {}", message);
						case 2 -> log.info("[plugin] {}", message);
						case 3 -> log.warn("[plugin] {}", message);
						default -> log.error("[plugin] {}", message);
					}
					return null;
				});
	}

}
