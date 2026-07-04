package org.alexmond.jhelm.core.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.exception.JhelmException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Symmetric encrypt/decrypt round-trip, the recursive {@code {cipher}} decryption pass
 * over a values tree, and disabled / fail-on-error behavior for {@link ValueEncryptor}.
 */
class ValueEncryptorTest {

	private final ValueEncryptor enc = new ValueEncryptor("test-key", "deadbeef", true);

	@Test
	void testEncryptDecryptRoundTrip() {
		String token = enc.encrypt("s3cret");
		assertTrue(token.startsWith(ValueEncryptor.CIPHER_PREFIX), "encrypt prefixes with {cipher}");
		assertEquals("s3cret", enc.decrypt(token), "decrypt reverses encrypt");
	}

	@Test
	void testDecryptAcceptsBareCiphertext() {
		String bare = enc.encrypt("hello").substring(ValueEncryptor.CIPHER_PREFIX.length());
		assertEquals("hello", enc.decrypt(bare), "decrypt tolerates a token without the {cipher} prefix");
	}

	@Test
	void testDecryptValuesWalksNestedMapsAndLists() {
		String secret = enc.encrypt("db-pass");
		String tokenInList = enc.encrypt("item0");

		Map<String, Object> values = new LinkedHashMap<>();
		values.put("plain", "unchanged");
		Map<String, Object> db = new LinkedHashMap<>();
		db.put("password", secret);
		values.put("db", db);
		List<Object> list = new ArrayList<>();
		list.add(tokenInList);
		list.add("literal");
		values.put("items", list);

		enc.decryptValues(values);

		assertEquals("unchanged", values.get("plain"), "non-cipher strings are untouched");
		assertEquals("db-pass", ((Map<?, ?>) values.get("db")).get("password"), "nested map cipher decrypted");
		assertEquals("item0", ((List<?>) values.get("items")).get(0), "list cipher decrypted");
		assertEquals("literal", ((List<?>) values.get("items")).get(1), "list literal untouched");
	}

	@Test
	void testDisabledIsNoOp() {
		ValueEncryptor disabled = new ValueEncryptor(null, "deadbeef", true);
		assertFalse(disabled.isEnabled());

		Map<String, Object> values = new LinkedHashMap<>();
		values.put("password", "{cipher}deadbeefdeadbeef");
		disabled.decryptValues(values);
		assertEquals("{cipher}deadbeefdeadbeef", values.get("password"), "no key -> cipher values pass through");

		assertThrows(JhelmException.class, () -> disabled.encrypt("x"), "encrypt without a key throws");
	}

	@Test
	void testFailOnErrorTrueThrows() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("bad", "{cipher}not-valid-ciphertext");
		assertThrows(RuntimeException.class, () -> enc.decryptValues(values), "undecryptable value aborts");
	}

	@Test
	void testFailOnErrorFalseLeavesValueUntouched() {
		ValueEncryptor lenient = new ValueEncryptor("test-key", "deadbeef", false);
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("bad", "{cipher}not-valid-ciphertext");
		lenient.decryptValues(values);
		assertEquals("{cipher}not-valid-ciphertext", values.get("bad"), "lenient mode leaves the token in place");
	}

}
