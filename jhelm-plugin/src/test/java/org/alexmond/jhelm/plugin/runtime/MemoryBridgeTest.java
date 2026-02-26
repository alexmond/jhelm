package org.alexmond.jhelm.plugin.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryBridgeTest {

	@Test
	void packAndUnpackPtrLen() {
		int ptr = 1024;
		int len = 256;
		long packed = MemoryBridge.packPtrLen(ptr, len);

		assertEquals(ptr, MemoryBridge.unpackPtr(packed));
		assertEquals(len, MemoryBridge.unpackLen(packed));
	}

	@Test
	void packAndUnpackZeroValues() {
		long packed = MemoryBridge.packPtrLen(0, 0);
		assertEquals(0, MemoryBridge.unpackPtr(packed));
		assertEquals(0, MemoryBridge.unpackLen(packed));
	}

	@Test
	void packAndUnpackLargeValues() {
		int ptr = Integer.MAX_VALUE;
		int len = Integer.MAX_VALUE;
		long packed = MemoryBridge.packPtrLen(ptr, len);

		assertEquals(ptr, MemoryBridge.unpackPtr(packed));
		assertEquals(len, MemoryBridge.unpackLen(packed));
	}

	@Test
	void packAndUnpackMixedValues() {
		int ptr = 65536;
		int len = 42;
		long packed = MemoryBridge.packPtrLen(ptr, len);

		assertEquals(ptr, MemoryBridge.unpackPtr(packed));
		assertEquals(len, MemoryBridge.unpackLen(packed));
	}

}
