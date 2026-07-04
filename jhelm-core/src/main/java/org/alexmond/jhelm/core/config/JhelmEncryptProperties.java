package org.alexmond.jhelm.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for decrypting {@code {cipher}} values. Mirrors Spring Cloud Config's
 * symmetric {@code encrypt.*} settings so a token produced by a config server's
 * {@code /encrypt} endpoint (or the {@code jhelm encrypt} tool) decrypts identically.
 * <p>
 * Disabled until {@link #key} is set; with no key, {@code {cipher}} values pass through
 * untouched and rendering is unchanged.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.encrypt")
public class JhelmEncryptProperties {

	/**
	 * Symmetric key used to decrypt (and encrypt) {@code {cipher}} values. Setting it
	 * enables decryption. Must match the key used to encrypt the values (e.g. the config
	 * server's {@code encrypt.key}).
	 */
	private String key;

	/**
	 * Hex-encoded salt for key derivation. Must match the encrypting side (Spring Cloud's
	 * {@code encrypt.salt}); defaults to Spring's default of {@code deadbeef}.
	 */
	private String salt = "deadbeef";

	/**
	 * Whether an undecryptable {@code {cipher}} value aborts the operation. When
	 * {@code false}, the value is left untouched and a warning is logged. Defaults to
	 * {@code true}.
	 */
	private boolean failOnError = true;

}
