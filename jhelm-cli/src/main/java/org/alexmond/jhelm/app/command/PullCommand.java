package org.alexmond.jhelm.app.command;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.RepoManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm pull CHART}, downloading a chart from a repository or OCI
 * registry to a local directory. Supports Helm's {@code --repo} form for pulling directly
 * from a repository URL with inline credentials and TLS settings, plus {@code --untar}
 * (unpack the fetched archive) and {@code --verify}/{@code --prov} (fetch and check the
 * chart's PGP provenance).
 */
@Component
@CommandLine.Command(name = "pull", mixinStandardHelpOptions = true, description = "Download a chart from a repository")
@Slf4j
public class PullCommand implements Callable<Integer> {

	private final RepoManager repoManager;

	private final VerifyAction verifyAction;

	@CommandLine.Parameters(index = "0", description = "chart to pull: repo/chart, repo/chart:version, or oci://...")
	private String chart;

	@CommandLine.Option(names = { "--version" }, description = "chart version (required for repo charts)")
	private String version;

	@CommandLine.Option(names = { "--dest", "-d" }, defaultValue = ".", description = "destination directory")
	private String dest;

	@CommandLine.Option(names = { "--untar" },
			description = "unpack the chart after downloading and remove the .tgz archive")
	private boolean untar;

	@CommandLine.Option(names = { "--untardir" }, defaultValue = ".",
			description = "with --untar, the directory the chart is expanded into (default \".\")")
	private String untardir;

	@CommandLine.Option(names = { "--verify" }, description = "verify the chart's PGP provenance before using it")
	private boolean verify;

	@CommandLine.Option(names = { "--prov" }, description = "fetch the chart's provenance (.prov) file too")
	private boolean prov;

	@CommandLine.Option(names = { "--keyring" }, description = "keyring containing the public keys used for --verify")
	private String keyring;

	@CommandLine.Option(names = { "--repo" },
			description = "chart repository URL to pull the named chart from directly (no prior repo add)")
	private String repo;

	@CommandLine.Option(names = { "--username" }, description = "chart repository username (with --repo)")
	private String username;

	@CommandLine.Option(names = { "--password" }, description = "chart repository password (with --repo)")
	private String password;

	@CommandLine.Option(names = { "--cert-file" }, description = "identity certificate (PEM) for client TLS auth")
	private String certFile;

	@CommandLine.Option(names = { "--key-file" }, description = "identity key (PEM) for client TLS auth")
	private String keyFile;

	@CommandLine.Option(names = { "--ca-file" }, description = "verify server certificate against this CA bundle (PEM)")
	private String caFile;

	@CommandLine.Option(names = { "--insecure-skip-tls-verify" },
			description = "skip TLS verification of the repository")
	private boolean insecureSkipTlsVerify;

	@CommandLine.Option(names = { "--pass-credentials" },
			description = "send credentials to all domains, not just the repository host")
	private boolean passCredentials;

	/**
	 * Creates the command.
	 * @param repoManager the repository manager that downloads the chart
	 * @param verifyAction verifies a packaged chart's PGP provenance
	 */
	public PullCommand(RepoManager repoManager, VerifyAction verifyAction) {
		this.repoManager = repoManager;
		this.verifyAction = verifyAction;
	}

	// S5443: the work directory is created via NIO createTempDirectory with owner-only
	// permissions and holds only the downloaded chart archive, so the shared temp
	// directory is used safely here.
	@SuppressWarnings("java:S5443")
	@Override
	public Integer call() {
		boolean withProv = this.prov || this.verify;
		try {
			Path workDir = Files.createTempDirectory("jhelm-pull-");
			try {
				pullInto(workDir.toString(), withProv);
				File tgz = findArchive(workDir);
				if (this.verify) {
					this.verifyAction.verify(tgz.getPath(),
							(this.keyring != null) ? this.keyring : defaultKeyringPath());
				}
				deliver(tgz);
				return CommandLine.ExitCode.OK;
			}
			finally {
				deleteRecursively(workDir);
			}
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error pulling chart: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	private void pullInto(String destDir, boolean withProv) throws IOException {
		if (this.repo != null && !this.repo.isBlank()) {
			this.repoManager.pullFromRepoUrl(this.repo, this.chart, this.version, destDir, buildRepoAuth(), withProv);
		}
		else {
			this.repoManager.pull(this.chart, this.version, destDir, withProv);
		}
	}

	// Unpacks (--untar) or moves the downloaded .tgz (and its .prov when requested) to
	// the
	// destination.
	private void deliver(File tgz) throws IOException {
		File provFile = new File(tgz.getParentFile(), tgz.getName() + ".prov");
		if (this.untar) {
			File target = new File((this.untardir != null && !this.untardir.isBlank()) ? this.untardir : ".");
			Files.createDirectories(target.toPath());
			this.repoManager.untar(tgz, target);
			if (this.prov && provFile.exists()) {
				moveInto(provFile, new File(this.dest));
			}
			CliOutput.println(CliOutput.success("Chart unpacked into " + target.getPath()));
		}
		else {
			File destDir = new File(this.dest);
			moveInto(tgz, destDir);
			if (this.prov && provFile.exists()) {
				moveInto(provFile, destDir);
			}
			CliOutput.println(CliOutput.success("Chart pulled to " + this.dest));
		}
	}

	private static void moveInto(File source, File targetDir) throws IOException {
		Files.createDirectories(targetDir.toPath());
		Files.move(source.toPath(), targetDir.toPath().resolve(source.getName()), StandardCopyOption.REPLACE_EXISTING);
	}

	private static File findArchive(Path workDir) throws IOException {
		try (var paths = Files.list(workDir)) {
			return paths.filter((p) -> p.getFileName().toString().endsWith(".tgz"))
				.findFirst()
				.map(Path::toFile)
				.orElseThrow(() -> new IOException("No chart archive was downloaded"));
		}
	}

	private static String defaultKeyringPath() {
		return System.getProperty("user.home") + "/.gnupg/pubring.gpg";
	}

	private static void deleteRecursively(Path dir) {
		try (var paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach((p) -> {
				try {
					Files.deleteIfExists(p);
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
		}
		catch (IOException | UncheckedIOException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to clean up temp pull dir {}: {}", dir, ex.getMessage());
			}
		}
	}

	private RepositoryConfig.Repository buildRepoAuth() {
		return RepositoryConfig.Repository.builder()
			.url(this.repo)
			.username(this.username)
			.password(this.password)
			.certFile(this.certFile)
			.keyFile(this.keyFile)
			.caFile(this.caFile)
			.insecureSkipTlsVerify(this.insecureSkipTlsVerify)
			.passCredentialsAll(this.passCredentials)
			.build();
	}

}
