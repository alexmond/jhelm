package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Encrypts and decrypts {@code {cipher}} values using the same symmetric primitive as
 * Spring Cloud Config ({@code Encryptors.text(key, salt)}), so tokens are interchangeable
 * between a config server's {@code /encrypt} endpoint, the {@code jhelm encrypt} tool,
 * and values files.
 * <p>
 * Disabled when no key is configured: {@link #decryptValues(Map)} is then a no-op and
 * {@code {cipher}} values pass through unchanged.
 */
@Slf4j
public class ValueEncryptor {

	/** Marker prefix identifying an encrypted value, matching Spring Cloud Config. */
	public static final String CIPHER_PREFIX = "{cipher}";

	private final boolean failOnError;

	private final TextEncryptor encryptor;

	/**
	 * @param key symmetric key; {@code null}/blank disables encryption
	 * @param salt hex salt for key derivation (must match the encrypting side)
	 * @param failOnError whether an undecryptable value aborts rather than passing
	 * through
	 */
	public ValueEncryptor(String key, String salt, boolean failOnError) {
		this.failOnError = failOnError;
		this.encryptor = (key != null && !key.isBlank()) ? Encryptors.text(key, salt) : null;
	}

	/**
	 * @return {@code true} when a key is configured and (de/en)cryption is available
	 */
	public boolean isEnabled() {
		return this.encryptor != null;
	}

	/**
	 * Encrypt a plaintext value into a {@code {cipher}<hex>} token.
	 * @param plaintext the value to encrypt
	 * @return the {@code {cipher}}-prefixed token
	 * @throws JhelmException if no key is configured
	 */
	public String encrypt(String plaintext) {
		requireEnabled();
		return CIPHER_PREFIX + this.encryptor.encrypt(plaintext);
	}

	/**
	 * Decrypt a value, tolerating either a bare hex ciphertext or a
	 * {@code {cipher}}-prefixed token.
	 * @param value the token (with or without the {@code {cipher}} prefix)
	 * @return the decrypted plaintext
	 * @throws JhelmException if no key is configured or the value cannot be decrypted
	 */
	public String decrypt(String value) {
		requireEnabled();
		String cipher = value.startsWith(CIPHER_PREFIX) ? value.substring(CIPHER_PREFIX.length()) : value;
		try {
			return this.encryptor.decrypt(cipher);
		}
		catch (RuntimeException ex) {
			throw new JhelmException("Failed to decrypt value: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Recursively decrypt every {@code {cipher}} string leaf in a values tree, in place.
	 * A no-op when disabled. On a decryption failure the value is left untouched and a
	 * warning logged, unless {@code fail-on-error} is set (then it throws).
	 * @param values the merged values map (mutated in place)
	 * @return the same map, for chaining
	 */
	public Map<String, Object> decryptValues(Map<String, Object> values) {
		if (isEnabled() && values != null) {
			decryptMap(values);
		}
		return values;
	}

	@SuppressWarnings("unchecked")
	private void decryptMap(Map<String, Object> map) {
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof String str) {
				if (str.startsWith(CIPHER_PREFIX)) {
					entry.setValue(decryptLeaf(str));
				}
			}
			else if (value instanceof Map<?, ?> nested) {
				decryptMap((Map<String, Object>) nested);
			}
			else if (value instanceof List<?> list) {
				decryptList((List<Object>) list);
			}
		}
	}

	private void decryptList(List<Object> list) {
		for (int i = 0; i < list.size(); i++) {
			Object value = list.get(i);
			if (value instanceof String str) {
				if (str.startsWith(CIPHER_PREFIX)) {
					list.set(i, decryptLeaf(str));
				}
			}
			else if (value instanceof Map<?, ?> nested) {
				decryptMap(castMap(nested));
			}
			else if (value instanceof List<?> nestedList) {
				decryptList(castList(nestedList));
			}
		}
	}

	private Object decryptLeaf(String token) {
		try {
			return decrypt(token);
		}
		catch (RuntimeException ex) {
			if (this.failOnError) {
				throw ex;
			}
			if (log.isWarnEnabled()) {
				log.warn("Leaving an undecryptable {cipher} value untouched: {}", ex.getMessage());
			}
			return token;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(Map<?, ?> map) {
		return (Map<String, Object>) map;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> castList(List<?> list) {
		return (List<Object>) list;
	}

	private void requireEnabled() {
		if (!isEnabled()) {
			throw new JhelmException("No encryption key configured (set jhelm.encrypt.key or pass --key)");
		}
	}

}
