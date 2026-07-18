package org.alexmond.jhelm.app.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.ParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelmPluginParameterExceptionHandlerTest {

	@TempDir
	Path pluginsDir;

	private ParameterException parseFailure(String... args) {
		try {
			new CommandLine(new Root()).parseArgs(args);
			throw new AssertionError("expected a parse failure");
		}
		catch (ParameterException ex) {
			return ex;
		}
	}

	private HelmPluginDispatcher dispatcher(HelmPluginRunner runner) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(JhelmAccessMode.FULL);
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get,
				Path.of("/home/tester"));
		Supplier<HelmPluginEnvironment> env = () -> HelmPluginEnvironment.builder().paths(paths).build();
		return HelmPluginDispatcher.forTesting(this.pluginsDir, env, new JhelmSecurityPolicy(props), runner);
	}

	private void installPlugin(String name) throws Exception {
		Path dir = Files.createDirectories(this.pluginsDir.resolve(name));
		Files.writeString(dir.resolve("plugin.yaml"), "name: " + name + "\ncommand: run-" + name + "\n");
	}

	@Test
	void dispatchesUnmatchedNameThatIsAPlugin() throws Exception {
		installPlugin("diff");
		AtomicReference<List<String>> ranCommand = new AtomicReference<>();
		HelmPluginRunner runner = (command, environment, dir) -> {
			ranCommand.set(command);
			return 42;
		};
		IParameterExceptionHandler delegate = (ex, args) -> {
			throw new AssertionError("delegate must not be called for a plugin");
		};
		HelmPluginParameterExceptionHandler handler = new HelmPluginParameterExceptionHandler(delegate,
				dispatcher(runner));

		int code = handler.handleParseException(parseFailure("diff", "upgrade", "rel"),
				new String[] { "diff", "upgrade", "rel" });

		assertEquals(42, code, "should return the plugin exit code");
		assertEquals(List.of("run-diff", "upgrade", "rel"), ranCommand.get());
	}

	@Test
	void delegatesWhenUnmatchedNameIsNotAPlugin() throws Exception {
		HelmPluginRunner runner = (command, environment, dir) -> {
			throw new AssertionError("runner must not be called for a non-plugin");
		};
		IParameterExceptionHandler delegate = (ex, args) -> 2;
		HelmPluginParameterExceptionHandler handler = new HelmPluginParameterExceptionHandler(delegate,
				dispatcher(runner));

		int code = handler.handleParseException(parseFailure("bogus"), new String[] { "bogus" });

		assertEquals(2, code, "should defer to the default handler");
	}

	@CommandLine.Command(name = "jhelm")
	static class Root {

	}

}
