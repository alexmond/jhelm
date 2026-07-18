package org.alexmond.jhelm.app.plugin;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * The Helm {@code plugin.yaml} manifest, as published by real Helm plugins (helm-diff,
 * helm-secrets, helm-s3, …). This is deliberately distinct from the WASM-centric
 * {@code org.alexmond.jhelm.plugin.model.PluginManifest}: it models Helm's native/exec
 * plugin contract (a {@code command} run as a subprocess) rather than a WASM module.
 *
 * <p>
 * Unknown properties are ignored so newer Helm fields (for example {@code platformHooks})
 * do not break parsing of an otherwise-usable plugin.
 *
 * @see <a href="https://helm.sh/docs/topics/plugins/">Helm plugin guide</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelmPluginManifest {

	/** Plugin name; also the top-level command it is invoked as ({@code helm <name>}). */
	private String name;

	/** Plugin version (SemVer, informational). */
	private String version;

	/** One-line usage string shown in listings. */
	private String usage;

	/** Longer description. */
	private String description;

	/**
	 * The command to run, as a single string that is whitespace-split into argv and
	 * environment-expanded (notably {@code $HELM_PLUGIN_DIR}). Used when no
	 * {@link #platformCommand} entry matches the current OS/architecture.
	 */
	private String command;

	/** Per-OS/architecture command overrides, matched before {@link #command}. */
	private List<PlatformCommand> platformCommand = new ArrayList<>();

	/** Lifecycle hooks run on install/update/delete. */
	private Hooks hooks;

	/** Downloader declarations: which URL protocols this plugin can fetch. */
	private List<Downloader> downloaders = new ArrayList<>();

	/**
	 * When {@code true}, Helm passes the plugin only the positional args and strips
	 * flags; when {@code false} (default) all trailing args are forwarded verbatim.
	 */
	private boolean ignoreFlags;

	/** A per-platform {@code command} override. */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class PlatformCommand {

		/** {@code GOOS} value to match (for example {@code linux}, {@code darwin}). */
		private String os;

		/** {@code GOARCH} value to match (for example {@code amd64}, {@code arm64}). */
		private String arch;

		/** The command string for this platform (same syntax as {@link #command}). */
		private String command;

	}

	/** Lifecycle hook commands, each a single command string. */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Hooks {

		/** Run after the plugin is installed. */
		private String install;

		/** Run after the plugin is updated. */
		private String update;

		/** Run before the plugin is uninstalled. */
		private String delete;

	}

	/** A downloader declaration binding a command to one or more URL protocols. */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Downloader {

		/** The command that fetches a chart for the declared {@link #protocols}. */
		private String command;

		/** URL schemes this downloader handles (for example {@code s3}, {@code gs}). */
		private List<String> protocols = new ArrayList<>();

	}

}
