package org.alexmond.jhelm.core.util;

import java.util.regex.Pattern;

/**
 * Splits a rendered multi-document YAML manifest into its individual documents.
 */
public final class ManifestDocuments {

	// A YAML document separator is a line that is exactly `---` (optionally with trailing
	// whitespace or a trailing comment). Splitting on the bare string `---` would also
	// match
	// it inside content or a comment — e.g. a decorative `# ---- section ----` — and
	// mangle
	// the manifest into invalid fragments (issue #713). Anchor to a whole line.
	private static final Pattern DOC_SEPARATOR = Pattern.compile("(?m)^---[ \\t]*(#.*)?$");

	private ManifestDocuments() {
	}

	/**
	 * Splits a manifest into its YAML documents on document-separator lines (a line that
	 * is exactly {@code ---}, optionally with trailing whitespace or a comment). A
	 * {@code ---} appearing inside document content or a comment is left intact.
	 * @param manifest the rendered manifest (must be non-null)
	 * @return the document chunks in order; some may be blank (callers skip those)
	 */
	public static String[] split(String manifest) {
		return DOC_SEPARATOR.split(manifest);
	}

}
