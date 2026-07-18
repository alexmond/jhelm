package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.service.ChartDownloader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bridges jhelm's {@link ChartDownloader} SPI to installed Helm <em>downloader</em>
 * plugins (helm-s3, helm-gcs, …). When a chart URL uses a custom scheme a plugin declares
 * (its {@code plugin.yaml} {@code downloaders[].protocols}), the plugin's downloader
 * command is run with Helm's contract — {@code command <cert> <key> <ca> <full-URL>} —
 * and the chart bytes are read from its standard output.
 *
 * <p>
 * Registered as a bean, so jhelm's core auto-configuration wires it into
 * {@code RepoManager} alongside the built-in HTTP/OCI paths. Running the plugin is gated
 * on the CLI's {@code FULL} security mode (the plugin is arbitrary local code).
 */
@Component
public class HelmPluginChartDownloader implements ChartDownloader {

	private final HelmPluginPaths paths;

	private final Supplier<HelmPluginEnvironment> environment;

	private final JhelmSecurityPolicy policy;

	/**
	 * Spring constructor. The environment factory is injected as an
	 * {@link ObjectProvider} and resolved lazily at download time: it depends on
	 * {@code RepoManager}, which in turn registers this downloader, so an eager
	 * dependency here would be circular.
	 * @param environmentFactory provides the {@code HELM_*} environment builder (resolved
	 * lazily)
	 * @param policy the security policy that gates native plugin execution
	 */
	@Autowired
	public HelmPluginChartDownloader(ObjectProvider<HelmPluginEnvironmentFactory> environmentFactory,
			JhelmSecurityPolicy policy) {
		this(HelmPluginPaths.fromEnvironment(), () -> environmentFactory.getObject().create(), policy);
	}

	HelmPluginChartDownloader(HelmPluginPaths paths, Supplier<HelmPluginEnvironment> environment,
			JhelmSecurityPolicy policy) {
		this.paths = paths;
		this.environment = environment;
		this.policy = policy;
	}

	@Override
	public boolean supportsProtocol(String protocol) {
		return findDownloader(protocol).isPresent();
	}

	@Override
	public byte[] download(String url) throws IOException {
		int schemeEnd = url.indexOf("://");
		String scheme = (schemeEnd > 0) ? url.substring(0, schemeEnd) : url;
		Match match = findDownloader(scheme)
			.orElseThrow(() -> new IOException("no Helm downloader plugin handles scheme '" + scheme + '\''));
		if (this.policy.mode() != JhelmAccessMode.FULL) {
			throw new IOException("running native Helm plugins is disabled in READ_ONLY mode — set "
					+ "jhelm.security.mode=FULL to enable");
		}
		return invoke(match, url);
	}

	private Optional<Match> findDownloader(String protocol) {
		for (DiscoveredHelmPlugin plugin : new HelmPluginDiscovery(this.paths).discover()) {
			for (HelmPluginManifest.Downloader downloader : plugin.manifest().getDownloaders()) {
				boolean handles = downloader.getProtocols().stream().anyMatch((p) -> p.equalsIgnoreCase(protocol));
				if (handles && downloader.getCommand() != null && !downloader.getCommand().isBlank()) {
					return Optional.of(new Match(plugin, downloader.getCommand()));
				}
			}
		}
		return Optional.empty();
	}

	private byte[] invoke(Match match, String url) throws IOException {
		Map<String, String> env = this.environment.get().forPlugin(match.plugin());
		String expanded = DiscoveredHelmPlugin.expand(match.command(), env);
		List<String> argv = new ArrayList<>(DiscoveredHelmPlugin.tokenize(expanded));
		if (argv.isEmpty()) {
			throw new IOException("downloader plugin '" + match.plugin().name() + "' declares no command");
		}
		// Helm's downloader contract: <cert-file> <key-file> <ca-file> <full-URL>.
		argv.add("");
		argv.add("");
		argv.add("");
		argv.add(url);
		ProcessBuilder builder = new ProcessBuilder(argv).directory(match.plugin().directory().toFile());
		builder.environment().putAll(env);
		Process process = builder.start();
		CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
		byte[] chart = readAll(process.getInputStream());
		int code;
		try {
			code = process.waitFor();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted while running downloader plugin '" + match.plugin().name() + '\'', ex);
		}
		if (code != 0) {
			String message = new String(stderr.join(), StandardCharsets.UTF_8).strip();
			throw new IOException(
					"downloader plugin '" + match.plugin().name() + "' failed (exit " + code + "): " + message);
		}
		return chart;
	}

	private static byte[] readAll(InputStream in) {
		try (in) {
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	// Retained for test construction over an explicit plugins directory.
	static HelmPluginChartDownloader forTesting(Path pluginsDir, Supplier<HelmPluginEnvironment> environment,
			JhelmSecurityPolicy policy) {
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", pluginsDir.toString())::get,
				Path.of(System.getProperty("user.home", ".")));
		return new HelmPluginChartDownloader(paths, environment, policy);
	}

	private record Match(DiscoveredHelmPlugin plugin, String command) {
	}

}
