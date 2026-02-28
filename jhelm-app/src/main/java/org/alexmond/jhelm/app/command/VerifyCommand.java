package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "verify", mixinStandardHelpOptions = true,
		description = "Verify that a chart has a valid provenance file")
@Slf4j
public class VerifyCommand implements Runnable {

	private final VerifyAction verifyAction;

	@CommandLine.Parameters(index = "0", description = "path to the chart archive (.tgz)")
	private String chartPath;

	@CommandLine.Option(names = { "--keyring" }, description = "path to the PGP public keyring file")
	private String keyring;

	public VerifyCommand(VerifyAction verifyAction) {
		this.verifyAction = verifyAction;
	}

	@Override
	public void run() {
		try {
			String resolvedKeyring = (keyring != null) ? keyring : defaultKeyringPath();
			verifyAction.verify(chartPath, resolvedKeyring);
			CliOutput.println(CliOutput.success("Verification succeeded"));
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error: " + ex.getMessage()));
		}
	}

	private String defaultKeyringPath() {
		return System.getProperty("user.home") + "/.gnupg/pubring.gpg";
	}

}
