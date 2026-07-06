package org.alexmond.jhelm.app.command;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.DependencyUpdateAction;
import org.alexmond.jhelm.core.action.PackageAction;
import org.alexmond.jhelm.core.service.SigningKey;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm package CHART}, packaging a chart directory into a {@code .tgz}
 * archive, optionally signing it with a PGP key.
 */
@Component
@CommandLine.Command(name = "package", mixinStandardHelpOptions = true,
		description = "Package a chart directory into a chart archive")
@Slf4j
public class PackageCommand implements Callable<Integer> {

	private final PackageAction packageAction;

	private final DependencyUpdateAction dependencyUpdateAction;

	@CommandLine.Parameters(index = "0", description = "path to the chart directory")
	private String chartPath;

	@CommandLine.Option(names = { "--version" }, description = "set the version on the packaged chart")
	private String version;

	@CommandLine.Option(names = { "--app-version" }, description = "set the appVersion on the packaged chart")
	private String appVersion;

	@CommandLine.Option(names = { "-u", "--dependency-update" },
			description = "update dependencies from Chart.yaml before packaging")
	private boolean dependencyUpdate;

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

	/**
	 * Creates the command.
	 * @param packageAction the action that packages and optionally signs the chart
	 * @param dependencyUpdateAction updates the chart's dependencies for {@code -u}
	 */
	public PackageCommand(PackageAction packageAction, DependencyUpdateAction dependencyUpdateAction) {
		this.packageAction = packageAction;
		this.dependencyUpdateAction = dependencyUpdateAction;
	}

	@Override
	public Integer call() {
		try {
			if (dependencyUpdate) {
				dependencyUpdateAction.update(new File(chartPath), List.of(), false);
			}
			packageAction.setDestination(new File(destination));
			File archive;

			if (sign) {
				if (keyId == null) {
					CliOutput.errPrintln(CliOutput.error("--key is required when --sign is specified"));
					return CommandLine.ExitCode.SOFTWARE;
				}
				String resolvedKeyring = (keyring != null) ? keyring : defaultKeyringPath();
				char[] passphrase = loadPassphrase();
				archive = packageAction.packageChart(chartPath, resolvedKeyring, keyId, passphrase, version,
						appVersion);
			}
			else {
				archive = packageAction.packageChart(chartPath, (SigningKey) null, null, version, appVersion);
			}

			CliOutput.println(CliOutput.success("Successfully packaged chart: " + archive.getName()));
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error packaging chart: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	private char[] loadPassphrase() throws IOException {
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
