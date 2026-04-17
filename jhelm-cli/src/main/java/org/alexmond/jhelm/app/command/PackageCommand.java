package org.alexmond.jhelm.app.command;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.PackageAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "package", mixinStandardHelpOptions = true,
		description = "Package a chart directory into a chart archive")
@Slf4j
public class PackageCommand implements Runnable {

	private final PackageAction packageAction;

	@CommandLine.Parameters(index = "0", description = "path to the chart directory")
	private String chartPath;

	@CommandLine.Option(names = { "-d", "--destination" }, defaultValue = ".",
			description = "destination directory for the packaged chart")
	private String destination;

	@CommandLine.Option(names = { "--sign" }, description = "sign the package using a PGP key")
	private boolean sign;

	@CommandLine.Option(names = { "--key" }, description = "key UID to use when signing (substring match)")
	private String keyId;

	@CommandLine.Option(names = { "--keyring" }, description = "path to the PGP secret keyring file")
	private String keyring;

	@CommandLine.Option(names = { "--passphrase-file" },
			description = "path to a file containing the passphrase for the signing key")
	private String passphraseFile;

	public PackageCommand(PackageAction packageAction) {
		this.packageAction = packageAction;
	}

	@Override
	public void run() {
		try {
			packageAction.setDestination(new File(destination));
			File archive;

			if (sign) {
				if (keyId == null) {
					CliOutput.errPrintln(CliOutput.error("--key is required when --sign is specified"));
					return;
				}
				String resolvedKeyring = (keyring != null) ? keyring : defaultKeyringPath();
				char[] passphrase = loadPassphrase();
				archive = packageAction.packageChart(chartPath, resolvedKeyring, keyId, passphrase);
			}
			else {
				archive = packageAction.packageChart(chartPath);
			}

			CliOutput.println(CliOutput.success("Successfully packaged chart: " + archive.getName()));
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error packaging chart: " + ex.getMessage()));
		}
	}

	private char[] loadPassphrase() throws Exception {
		if (passphraseFile != null) {
			return Files.readString(Path.of(passphraseFile)).trim().toCharArray();
		}
		String envPassphrase = System.getenv("HELM_PASSPHRASE");
		if (envPassphrase != null) {
			return envPassphrase.toCharArray();
		}
		throw new IllegalArgumentException(
				"Passphrase required: use --passphrase-file or set HELM_PASSPHRASE environment variable");
	}

	private String defaultKeyringPath() {
		return System.getProperty("user.home") + "/.gnupg/secring.gpg";
	}

}
