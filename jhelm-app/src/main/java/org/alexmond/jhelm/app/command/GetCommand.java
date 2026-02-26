package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.model.Release;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Map;
import java.util.Optional;

@Component
@CommandLine.Command(name = "get", mixinStandardHelpOptions = true,
		description = "Download extended information of a named release",
		subcommands = { GetCommand.ValuesCommand.class, GetCommand.ManifestCommand.class, GetCommand.NotesCommand.class,
				GetCommand.HooksCommand.class, GetCommand.MetadataCommand.class, GetCommand.AllCommand.class })
@Slf4j
public class GetCommand implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	private static Optional<Release> resolveRelease(GetAction getAction, String name, String namespace, int revision)
			throws Exception {
		if (revision > 0) {
			return getAction.getReleaseByRevision(name, namespace, revision);
		}
		return getAction.getRelease(name, namespace);
	}

	@Component
	@CommandLine.Command(name = "values", mixinStandardHelpOptions = true,
			description = "Download the values file for a named release")
	@Slf4j
	public static class ValuesCommand implements Runnable {

		private final GetAction getAction;

		@CommandLine.Parameters(index = "0", description = "release name")
		String releaseName;

		@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
		String namespace;

		@CommandLine.Option(names = { "--revision" }, defaultValue = "-1",
				description = "get the named release with revision")
		int revision;

		@CommandLine.Option(names = { "-a", "--all" }, description = "dump all (computed) values")
		boolean allValues;

		@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "yaml",
				description = "output format (yaml or json)")
		String output;

		public ValuesCommand(GetAction getAction) {
			this.getAction = getAction;
		}

		@Override
		public void run() {
			try {
				Optional<Release> releaseOpt = resolveRelease(getAction, releaseName, namespace, revision);
				if (releaseOpt.isEmpty()) {
					CliOutput.errPrintln(CliOutput.error("Error: release not found: " + releaseName));
					return;
				}
				Release release = releaseOpt.get();
				if ("json".equalsIgnoreCase(output)) {
					Map<String, Object> values;
					if (release.getConfig() != null && release.getConfig().getValues() != null) {
						values = release.getConfig().getValues();
					}
					else {
						values = Map.of();
					}
					System.out.println(getAction.toJson(values));
				}
				else {
					System.out.println(getAction.getValues(release, allValues));
				}
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error getting values: " + ex.getMessage()));
			}
		}

	}

	@Component
	@CommandLine.Command(name = "manifest", mixinStandardHelpOptions = true,
			description = "Download the manifest for a named release")
	@Slf4j
	public static class ManifestCommand implements Runnable {

		private final GetAction getAction;

		@CommandLine.Parameters(index = "0", description = "release name")
		String releaseName;

		@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
		String namespace;

		@CommandLine.Option(names = { "--revision" }, defaultValue = "-1",
				description = "get the named release with revision")
		int revision;

		public ManifestCommand(GetAction getAction) {
			this.getAction = getAction;
		}

		@Override
		public void run() {
			try {
				Optional<Release> releaseOpt = resolveRelease(getAction, releaseName, namespace, revision);
				if (releaseOpt.isEmpty()) {
					CliOutput.errPrintln(CliOutput.error("Error: release not found: " + releaseName));
					return;
				}
				System.out.println(getAction.getManifest(releaseOpt.get()));
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error getting manifest: " + ex.getMessage()));
			}
		}

	}

	@Component
	@CommandLine.Command(name = "notes", mixinStandardHelpOptions = true,
			description = "Download the notes for a named release")
	@Slf4j
	public static class NotesCommand implements Runnable {

		private final GetAction getAction;

		@CommandLine.Parameters(index = "0", description = "release name")
		String releaseName;

		@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
		String namespace;

		@CommandLine.Option(names = { "--revision" }, defaultValue = "-1",
				description = "get the named release with revision")
		int revision;

		public NotesCommand(GetAction getAction) {
			this.getAction = getAction;
		}

		@Override
		public void run() {
			try {
				Optional<Release> releaseOpt = resolveRelease(getAction, releaseName, namespace, revision);
				if (releaseOpt.isEmpty()) {
					CliOutput.errPrintln(CliOutput.error("Error: release not found: " + releaseName));
					return;
				}
				String notes = getAction.getNotes(releaseOpt.get());
				if (notes.isEmpty()) {
					System.out.println("No notes found for release");
				}
				else {
					System.out.println(notes);
				}
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error getting notes: " + ex.getMessage()));
			}
		}

	}

	@Component
	@CommandLine.Command(name = "hooks", mixinStandardHelpOptions = true,
			description = "Download all hooks for a named release")
	@Slf4j
	public static class HooksCommand implements Runnable {

		private final GetAction getAction;

		@CommandLine.Parameters(index = "0", description = "release name")
		String releaseName;

		@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
		String namespace;

		@CommandLine.Option(names = { "--revision" }, defaultValue = "-1",
				description = "get the named release with revision")
		int revision;

		public HooksCommand(GetAction getAction) {
			this.getAction = getAction;
		}

		@Override
		public void run() {
			try {
				Optional<Release> releaseOpt = resolveRelease(getAction, releaseName, namespace, revision);
				if (releaseOpt.isEmpty()) {
					CliOutput.errPrintln(CliOutput.error("Error: release not found: " + releaseName));
					return;
				}
				String hooks = getAction.getHooks(releaseOpt.get());
				if (hooks.isEmpty()) {
					System.out.println("No hooks found for release");
				}
				else {
					System.out.println(hooks);
				}
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error getting hooks: " + ex.getMessage()));
			}
		}

	}

	@Component
	@CommandLine.Command(name = "metadata", mixinStandardHelpOptions = true,
			description = "Download the metadata for a named release")
	@Slf4j
	public static class MetadataCommand implements Runnable {

		private final GetAction getAction;

		@CommandLine.Parameters(index = "0", description = "release name")
		String releaseName;

		@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
		String namespace;

		@CommandLine.Option(names = { "--revision" }, defaultValue = "-1",
				description = "get the named release with revision")
		int revision;

		@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "yaml",
				description = "output format (yaml or json)")
		String output;

		public MetadataCommand(GetAction getAction) {
			this.getAction = getAction;
		}

		@Override
		public void run() {
			try {
				Optional<Release> releaseOpt = resolveRelease(getAction, releaseName, namespace, revision);
				if (releaseOpt.isEmpty()) {
					CliOutput.errPrintln(CliOutput.error("Error: release not found: " + releaseName));
					return;
				}
				Map<String, Object> metadata = getAction.getMetadata(releaseOpt.get());
				if ("json".equalsIgnoreCase(output)) {
					System.out.println(getAction.toJson(metadata));
				}
				else {
					System.out.println(getAction.toYaml(metadata));
				}
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error getting metadata: " + ex.getMessage()));
			}
		}

	}

	@Component
	@CommandLine.Command(name = "all", mixinStandardHelpOptions = true,
			description = "Download all information for a named release")
	@Slf4j
	public static class AllCommand implements Runnable {

		private final GetAction getAction;

		@CommandLine.Parameters(index = "0", description = "release name")
		String releaseName;

		@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
		String namespace;

		@CommandLine.Option(names = { "--revision" }, defaultValue = "-1",
				description = "get the named release with revision")
		int revision;

		public AllCommand(GetAction getAction) {
			this.getAction = getAction;
		}

		@Override
		public void run() {
			try {
				Optional<Release> releaseOpt = resolveRelease(getAction, releaseName, namespace, revision);
				if (releaseOpt.isEmpty()) {
					CliOutput.errPrintln(CliOutput.error("Error: release not found: " + releaseName));
					return;
				}
				System.out.println(getAction.getAll(releaseOpt.get(), false));
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error getting release info: " + ex.getMessage()));
			}
		}

	}

}
