package org.alexmond.jhelm.app.command;

import java.util.concurrent.Callable;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.config.JhelmEncryptProperties;
import org.alexmond.jhelm.core.service.ValueEncryptor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Implements {@code jhelm encrypt}, turning a plaintext value into a
 * {@code {cipher}<hex>} token. The token is interchangeable with Spring Cloud Config:
 * paste it into a config-server repo or drop it into a jhelm values file, and jhelm
 * decrypts it at render time with the same key.
 */
@Component
@CommandLine.Command(name = "encrypt", mixinStandardHelpOptions = true,
		description = "Encrypt a value into a {cipher} token (compatible with Spring Cloud Config)")
public class EncryptCommand implements Callable<Integer> {

	private final JhelmEncryptProperties encryptProperties;

	@Parameters(index = "0", arity = "0..1", description = "value to encrypt (read from stdin if omitted)")
	private String value;

	@Option(names = { "--key" }, description = "symmetric key (default: jhelm.encrypt.key)")
	private String key;

	@Option(names = { "--salt" }, description = "hex salt (default: jhelm.encrypt.salt, or deadbeef)")
	private String salt;

	/**
	 * Creates the command.
	 * @param encryptProperties the configured {@code jhelm.encrypt.*} defaults
	 */
	public EncryptCommand(JhelmEncryptProperties encryptProperties) {
		this.encryptProperties = encryptProperties;
	}

	@Override
	public Integer call() {
		try {
			ValueEncryptor encryptor = EncryptSupport.resolve(key, salt, encryptProperties);
			CliOutput.println(encryptor.encrypt(EncryptSupport.readInput(value)));
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error encrypting value: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
