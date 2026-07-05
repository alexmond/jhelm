package org.alexmond.jhelm.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-processes a rendered manifest string using its Helm-style
 * {@code # Source: <chart>/templates/<file>} markers: filter to selected templates
 * ({@code --show-only}), drop chart test hooks ({@code --skip-tests}), and group
 * documents by source template ({@code --output-dir}). The engine emits one
 * {@code # Source:} marker per template block, so a document produced after an
 * in-template {@code ---} inherits the marker of the block it belongs to.
 */
public final class RenderedManifest {

	private static final Pattern SOURCE = Pattern.compile("(?m)^# Source: (.+)$");

	// Matches the test-hook annotation Helm's `--skip-tests` filters on: `helm.sh/hook:
	// test`
	// (and the legacy `test-success` / `test-failure` values), with optional quoting.
	private static final Pattern TEST_HOOK = Pattern.compile("(?m)helm\\.sh/hook[\"']?\\s*:\\s*[\"']?\\s*test");

	private RenderedManifest() {
	}

	/**
	 * Splits a rendered manifest into documents on {@code ---} separators, attributing
	 * each to its {@code # Source:} marker (a document without its own marker inherits
	 * the previous one's).
	 * @param manifest the rendered manifest, or {@code null}
	 * @return the documents in order (empty if the manifest is blank)
	 */
	public static List<Document> parse(String manifest) {
		List<Document> docs = new ArrayList<>();
		if (manifest == null || manifest.isBlank()) {
			return docs;
		}
		String lastSource = null;
		for (String chunk : manifest.split("\\r?\\n---\\r?\\n")) {
			if (chunk.isBlank()) {
				continue;
			}
			Matcher matcher = SOURCE.matcher(chunk);
			String source = matcher.find() ? matcher.group(1).trim() : lastSource;
			lastSource = source;
			docs.add(new Document(source, chunk.strip()));
		}
		return docs;
	}

	/**
	 * Joins documents back into a manifest string, separating them with {@code ---} and
	 * no trailing separator (matching the engine's cleaned output).
	 * @param docs the documents to join
	 * @return the joined manifest
	 */
	public static String join(List<Document> docs) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < docs.size(); i++) {
			if (i > 0) {
				sb.append("---\n");
			}
			sb.append(docs.get(i).text()).append('\n');
		}
		return sb.toString();
	}

	/**
	 * Keeps only the documents whose source matches one of the requested template paths.
	 * A request matches a source when it equals the full source path or is a trailing
	 * path segment of it (so {@code templates/foo.yaml} selects
	 * {@code mychart/templates/foo.yaml}). Mirrors {@code helm template --show-only},
	 * including its error when a requested template matches nothing.
	 * @param manifest the rendered manifest
	 * @param templates the requested template paths (repeatable {@code --show-only}
	 * values)
	 * @return the filtered manifest, documents ordered by the request order
	 * @throws IllegalArgumentException if a requested template matches no rendered
	 * document
	 */
	public static String showOnly(String manifest, List<String> templates) {
		List<Document> docs = parse(manifest);
		List<Document> selected = new ArrayList<>();
		for (String request : templates) {
			String normalized = request.replace('\\', '/').strip();
			List<Document> matches = docs.stream().filter((doc) -> matchesSource(doc.source(), normalized)).toList();
			if (matches.isEmpty()) {
				throw new IllegalArgumentException("could not find template " + request + " in chart");
			}
			selected.addAll(matches);
		}
		return join(selected);
	}

	private static boolean matchesSource(String source, String request) {
		if (source == null) {
			return false;
		}
		return source.equals(request) || source.endsWith("/" + request);
	}

	/**
	 * Drops documents carrying a Helm test-hook annotation ({@code helm.sh/hook: test}),
	 * mirroring {@code helm template --skip-tests}.
	 * @param manifest the rendered manifest
	 * @return the manifest with test-hook documents removed
	 */
	public static String skipTests(String manifest) {
		List<Document> kept = parse(manifest).stream().filter((doc) -> !TEST_HOOK.matcher(doc.text()).find()).toList();
		return join(kept);
	}

	/**
	 * Groups the manifest's documents by source template path, preserving order, so each
	 * group can be written to its own file under an output directory. Documents without a
	 * source are grouped under the empty key.
	 * @param manifest the rendered manifest
	 * @return an ordered map of source path to the joined documents rendered from it
	 */
	public static Map<String, String> groupBySource(String manifest) {
		Map<String, List<Document>> bySource = new LinkedHashMap<>();
		for (Document doc : parse(manifest)) {
			bySource.computeIfAbsent((doc.source() != null) ? doc.source() : "", (key) -> new ArrayList<>()).add(doc);
		}
		Map<String, String> result = new LinkedHashMap<>();
		bySource.forEach((source, docs) -> result.put(source, join(docs)));
		return result;
	}

	/**
	 * A single manifest document: the source template path it was rendered from (may be
	 * {@code null} for content emitted without a marker) and its full text including the
	 * marker line.
	 *
	 * @param source the {@code <chart>/templates/<file>} path, or {@code null} if
	 * unattributed
	 * @param text the document text (marker line included), stripped of surrounding
	 * whitespace
	 */
	public record Document(String source, String text) {
	}

}
