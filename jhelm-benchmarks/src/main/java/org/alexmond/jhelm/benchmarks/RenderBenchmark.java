package org.alexmond.jhelm.benchmarks;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;

/**
 * Render benchmarks for {@code jhelm-core}. Network-free: renders a bundled
 * representative chart ({@code charts/bench-app} — nested values, a library subchart, and
 * templates that lean on {@code include}/{@code toYaml}/{@code range}) so the numbers
 * track the real render hot path (function registry, named-template resolution, values
 * coalescing, YAML conversion).
 *
 * <p>
 * {@link #render} is the steady-state target: one warm {@link Engine} (registry + parse
 * cache warm), rendering the same chart repeatedly — the shape the render-path perf
 * tickets (#718 values deep-copy, #719 double-parse, #720 {@code toYaml} churn) move.
 * Pair with {@code -prof gc} for allocation. {@link #renderFreshEngine} builds a new
 * engine per op to show the per-engine construction cost that the shared registry (#717)
 * amortises.
 *
 * <p>
 * Run: {@code java -jar jhelm-benchmarks/target/benchmarks.jar RenderBenchmark -prof gc}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class RenderBenchmark {

	private Path chartDir;

	private Chart chart;

	private final Map<String, Object> userValues = Map.of("replicaCount", 5, "image", Map.of("tag", "2.0.0-bench"));

	private final ReleaseContext release = ReleaseContext.builder()
		.name("bench")
		.namespace("default")
		.revision(1)
		.build();

	private Engine warmEngine;

	@Setup
	public void setup() throws Exception {
		this.chartDir = materializeChart();
		this.chart = new ChartLoader().load(this.chartDir.toFile());
		this.warmEngine = new Engine(); // default parse cache on, shared registry
		// Warm the reflection/parse caches so steady-state numbers exclude first-render
		// warmup.
		this.warmEngine.render(this.chart, this.userValues, this.release);
	}

	/** Steady-state render through one warm engine — the render-path perf target. */
	@Benchmark
	public String render() {
		return this.warmEngine.render(this.chart, this.userValues, this.release);
	}

	/**
	 * Render through a freshly-built engine each op — shows per-engine construction cost.
	 */
	@Benchmark
	public String renderFreshEngine() {
		return new Engine().render(this.chart, this.userValues, this.release);
	}

	/** One-time chart load from disk (parse of Chart.yaml/values.yaml/templates). */
	@Benchmark
	public Chart loadChart() {
		return new ChartLoader().load(this.chartDir.toFile());
	}

	// --- bundled-chart materialisation (works from exploded classes and the shaded jar)
	// ---

	private static Path materializeChart() throws Exception {
		ClassLoader cl = RenderBenchmark.class.getClassLoader();
		URL marker = cl.getResource("charts/bench-app/Chart.yaml");
		if (marker == null) {
			throw new IllegalStateException("bundled chart 'charts/bench-app' not found on the classpath");
		}
		Path dest = Files.createTempDirectory("jhelm-bench-chart");
		if ("jar".equals(marker.getProtocol())) {
			String s = marker.toString();
			URI jarUri = URI.create(s.substring("jar:".length(), s.indexOf("!/")));
			try (FileSystem fs = FileSystems.newFileSystem(Path.of(jarUri))) {
				copyTree(fs.getPath("/charts/bench-app"), dest);
			}
		}
		else {
			copyTree(Path.of(marker.toURI()).getParent(), dest);
		}
		return dest;
	}

	private static void copyTree(Path src, Path dest) throws IOException {
		try (var walk = Files.walk(src)) {
			List<Path> entries = walk.toList();
			for (Path p : entries) {
				// relativize/rebuild via String so a jar-FS source path lands on the
				// default FS
				Path target = dest.resolve(src.relativize(p).toString());
				if (Files.isDirectory(p)) {
					Files.createDirectories(target);
				}
				else {
					Files.createDirectories(target.getParent());
					Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

}
