package org.alexmond.jhelm.plugin.runtime;

import java.nio.charset.StandardCharsets;

import com.dylibso.chicory.runtime.Instance;

/**
 * Utility for transferring data between Java strings/bytes and WASM linear memory.
 */
public final class MemoryBridge {

	private MemoryBridge() {
	}

	/**
	 * Write a Java string into WASM memory using the module's exported {@code alloc}
	 * function.
	 * @param instance the WASM instance
	 * @param data the string to write
	 * @return packed (ptr, len) as a single long
	 */
	public static long writeString(Instance instance, String data) {
		byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
		return writeBytes(instance, bytes);
	}

	/**
	 * Write bytes into WASM memory using the module's exported {@code alloc} function.
	 * @param instance the WASM instance
	 * @param data the bytes to write
	 * @return packed (ptr, len) as a single long
	 */
	public static long writeBytes(Instance instance, byte[] data) {
		var alloc = instance.export("alloc");
		long[] result = alloc.apply((long) data.length);
		int ptr = (int) result[0];
		instance.memory().write(ptr, data);
		return packPtrLen(ptr, data.length);
	}

	/**
	 * Read a UTF-8 string from WASM memory at the given (ptr, len).
	 * @param instance the WASM instance
	 * @param ptr the memory pointer
	 * @param len the byte length
	 * @return the decoded string
	 */
	public static String readString(Instance instance, int ptr, int len) {
		byte[] bytes = readBytes(instance, ptr, len);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	/**
	 * Read bytes from WASM memory at the given (ptr, len).
	 * @param instance the WASM instance
	 * @param ptr the memory pointer
	 * @param len the byte length
	 * @return the byte array
	 */
	public static byte[] readBytes(Instance instance, int ptr, int len) {
		return instance.memory().readBytes(ptr, len);
	}

	/**
	 * Pack a pointer and length into a single {@code long} for return values.
	 * @param ptr the pointer (upper 32 bits)
	 * @param len the length (lower 32 bits)
	 * @return the packed value
	 */
	public static long packPtrLen(int ptr, int len) {
		return ((long) ptr << 32) | (len & 0xFFFFFFFFL);
	}

	/**
	 * Unpack the pointer from a packed (ptr, len) value.
	 * @param packed the packed value
	 * @return the pointer
	 */
	public static int unpackPtr(long packed) {
		return (int) (packed >> 32);
	}

	/**
	 * Unpack the length from a packed (ptr, len) value.
	 * @param packed the packed value
	 * @return the length
	 */
	public static int unpackLen(long packed) {
		return (int) (packed & 0xFFFFFFFFL);
	}

}
