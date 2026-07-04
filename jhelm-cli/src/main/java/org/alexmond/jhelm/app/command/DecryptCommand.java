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
 * Implements {@code jhelm decrypt}, turning a {@code {cipher}} token (with or without the
 * prefix) back into plaintext — useful for verifying a value encrypted for a config
 * server or a jhelm values file.
 */
@Component
@CommandLine.Command(name = "decrypt", mixinStandardHelpOptions = true,
		description = "Decrypt a {cipher} token back to plaintext")
public class DecryptCommand implements Callable<Integer> {

	private final JhelmEncryptProperties encryptProperties;

	@Parameters(index = "0", arity = "0..1", description = "token to decrypt (read from stdin if omitted)")
	private String value;

	@Option(names = { "--key" }, description = "symmetric key (default: jhelm.encrypt.key)")
	private String key;

	@Option(names = { "--salt" }, description = "hex salt (default: jhelm.encrypt.salt, or deadbeef)")
	private String salt;

	/**
	 * Creates the command.
	 * @param encryptProperties the configured {@code jhelm.encrypt.*} defaults
	 */
	public DecryptCommand(JhelmEncryptProperties encryptProperties) {
		this.encryptProperties = encryptProperties;
	}

	@Override
	public Integer call() {
		try {
			ValueEncryptor encryptor = EncryptSupport.resolve(key, salt, encryptProperties);
			CliOutput.println(encryptor.decrypt(EncryptSupport.readInput(value)));
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error decrypting value: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
