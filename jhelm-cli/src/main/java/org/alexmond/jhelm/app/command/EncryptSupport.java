package org.alexmond.jhelm.app.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.alexmond.jhelm.core.config.JhelmEncryptProperties;
import org.alexmond.jhelm.core.service.ValueEncryptor;

/**
 * Shared helpers for the {@code encrypt} / {@code decrypt} commands: resolve the
 * effective key/salt (CLI flags override {@code jhelm.encrypt.*}) and read the value from
 * an argument or stdin.
 */
final class EncryptSupport {

	private EncryptSupport() {
	}

	/**
	 * Build a {@link ValueEncryptor} from CLI overrides falling back to configured
	 * properties. A blank key yields a disabled encryptor that throws a clear error on
	 * use.
	 * @param key {@code --key} override, or {@code null}
	 * @param salt {@code --salt} override, or {@code null}
	 * @param properties the configured {@code jhelm.encrypt.*} values
	 * @return the encryptor
	 */
	static ValueEncryptor resolve(String key, String salt, JhelmEncryptProperties properties) {
		String effectiveKey = (key != null && !key.isBlank()) ? key : properties.getKey();
		String effectiveSalt = (salt != null && !salt.isBlank()) ? salt : properties.getSalt();
		return new ValueEncryptor(effectiveKey, effectiveSalt, true);
	}

	/**
	 * Return the value argument, or the trimmed contents of stdin when it is omitted.
	 * @param value the positional value argument, or {@code null}
	 * @return the input to (de/en)crypt
	 * @throws IOException if stdin cannot be read
	 */
	static String readInput(String value) throws IOException {
		if (value != null) {
			return value;
		}
		return new String(System.in.readAllBytes(), StandardCharsets.UTF_8).strip();
	}

}
