package org.alexmond.jhelm.core.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.JhelmMetricsAutoConfiguration;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link PluginLoader} discovers jhelm plugins from JARs that are <em>not</em>
 * on the application classpath. Each plugin is compiled and packaged at test time so its
 * implementation class exists only inside the jar — proving genuine external loading
 * rather than classpath leakage — and the full auto-configuration wiring is exercised end
 * to end.
 */
class PluginLoaderTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(JhelmMetricsAutoConfiguration.class, JhelmCoreAutoConfiguration.class));

	@Test
	void emptyPathLoadsNothing() {
		try (PluginLoader loader = new PluginLoader(List.of())) {
			assertEquals(0, loader.jarCount());
			assertTrue(loader.load(JhelmPostRenderer.class).isEmpty());
		}
	}

	@Test
	void discoversAndIsolatesExternalPostRenderer(@TempDir Path tmp) throws Exception {
		Path pluginsDir = Files.createDirectories(tmp.resolve("plugins"));
		String fqcn = "ext.render.BannerPostRenderer";
		String source = """
				package ext.render;
				import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;
				import org.alexmond.jhelm.pluginapi.JhelmPluginException;
				public class BannerPostRenderer implements JhelmPostRenderer {
					public String postRender(String manifest) throws JhelmPluginException {
						return "# external\\n" + manifest;
					}
				}
				""";
		buildPluginJar(tmp, pluginsDir.resolve("banner-plugin.jar"), Map.of(fqcn, source),
				Map.of("org.alexmond.jhelm.pluginapi.JhelmPostRenderer", List.of(fqcn)));

		// The plugin class must not be reachable from the application classpath.
		assertThrows(ClassNotFoundException.class, () -> Class.forName(fqcn));

		try (PluginLoader loader = new PluginLoader(List.of(pluginsDir.toString()))) {
			assertEquals(1, loader.jarCount());
			List<JhelmPostRenderer> found = loader.load(JhelmPostRenderer.class);
			assertEquals(1, found.size());
			JhelmPostRenderer plugin = found.get(0);
			assertEquals(fqcn, plugin.getClass().getName());
			assertNotSame(PluginLoader.class.getClassLoader(), plugin.getClass().getClassLoader());
			assertEquals("# external\nspec: {}", plugin.postRender("spec: {}"));
		}
	}

	@Test
	void autoConfigWiresExternalTemplateFunctionIntoEngine(@TempDir Path tmp) throws Exception {
		Path pluginsDir = Files.createDirectories(tmp.resolve("plugins"));
		String fqcn = "ext.fn.ExtFunctions";
		String source = """
				package ext.fn;
				import java.util.Map;
				import org.alexmond.jhelm.pluginapi.JhelmTemplateFunction;
				import org.alexmond.jhelm.pluginapi.JhelmTemplateFunctionProvider;
				public class ExtFunctions implements JhelmTemplateFunctionProvider {
					public Map<String, JhelmTemplateFunction> functions() {
						return Map.of("ext_greet", (JhelmTemplateFunction) (args) -> "hi, " + args[0]);
					}
				}
				""";
		buildPluginJar(tmp, pluginsDir.resolve("fn-plugin.jar"), Map.of(fqcn, source),
				Map.of("org.alexmond.jhelm.pluginapi.JhelmTemplateFunctionProvider", List.of(fqcn)));

		this.contextRunner.withPropertyValues("jhelm.plugins.path=" + pluginsDir).run((ctx) -> {
			PluginLoader loader = ctx.getBean(PluginLoader.class);
			assertEquals(1, loader.jarCount());

			Engine engine = ctx.getBean(Engine.class);
			Chart chart = Chart.builder()
				.metadata(ChartMetadata.builder().name("mychart").version("1.0.0").build())
				.templates(List
					.of(Chart.Template.builder().name("cm.yaml").data("greeting: {{ ext_greet \"world\" }}").build()))
				.values(Map.of())
				.build();

			String result = engine.render(chart, Map.of(),
					ReleaseContext.builder().name("rel").namespace("default").install(true).revision(1).build());

			assertTrue(result.contains("greeting: hi, world"), result);
		});
	}

	/**
	 * Compiles the given sources against jhelm-plugin-api and packages them, with the
	 * given {@code META-INF/services} entries, into {@code outJar}.
	 */
	private static void buildPluginJar(Path workDir, Path outJar, Map<String, String> sources,
			Map<String, List<String>> services) throws Exception {
		Path srcDir = Files.createDirectories(workDir.resolve("src-" + outJar.getFileName()));
		Path classesDir = Files.createDirectories(workDir.resolve("classes-" + outJar.getFileName()));
		List<String> sourceFiles = new ArrayList<>();
		for (Map.Entry<String, String> entry : sources.entrySet()) {
			Path file = srcDir.resolve(entry.getKey().replace('.', '/') + ".java");
			Files.createDirectories(file.getParent());
			Files.writeString(file, entry.getValue());
			sourceFiles.add(file.toString());
		}

		String apiClasspath = Path
			.of(JhelmPostRenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI())
			.toString();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertTrue(compiler != null, "a JDK (system Java compiler) is required for this test");
		List<String> args = new ArrayList<>(List.of("-classpath", apiClasspath, "-d", classesDir.toString()));
		args.addAll(sourceFiles);
		int rc = compiler.run(null, null, null, args.toArray(String[]::new));
		assertEquals(0, rc, "external plugin compilation failed");

		try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(outJar))) {
			try (Stream<Path> walk = Files.walk(classesDir)) {
				List<Path> classFiles = walk.filter(Files::isRegularFile).sorted().toList();
				for (Path classFile : classFiles) {
					jar.putNextEntry(new JarEntry(classesDir.relativize(classFile).toString().replace('\\', '/')));
					Files.copy(classFile, jar);
					jar.closeEntry();
				}
			}
			for (Map.Entry<String, List<String>> service : services.entrySet()) {
				jar.putNextEntry(new JarEntry("META-INF/services/" + service.getKey()));
				jar.write(String.join("\n", service.getValue()).getBytes(StandardCharsets.UTF_8));
				jar.closeEntry();
			}
		}
	}

}
