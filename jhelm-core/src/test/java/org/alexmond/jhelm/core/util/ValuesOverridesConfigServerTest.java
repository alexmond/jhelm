package org.alexmond.jhelm.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Precedence of config-server values in {@link ValuesOverrides#parse} relative to
 * {@code -f} files and the {@code --set} family, including the {@code override-none} /
 * {@code override-system-properties} flags.
 */
class ValuesOverridesConfigServerTest {

	@TempDir
	File dir;

	private List<String> file;

	// file: a=file, d=fileD ; config server: a=cs, b=cs, d=csD ; --set: a=set
	private final Map<String, Object> configServer = Map.of("a", "cs", "b", "cs", "d", "csD");

	private final List<String> set = List.of("a=set");

	@BeforeEach
	void setUp() throws IOException {
		File f = new File(dir, "values.yaml");
		Files.writeString(f.toPath(), "a: file\nd: fileD\n");
		file = List.of(f.getPath());
	}

	private Map<String, Object> parse(boolean overrideNone, boolean overrideSystemProperties) throws IOException {
		return ValuesOverrides.parse(file, ValuesProfiles.none(), configServer, overrideNone, overrideSystemProperties,
				set, null, null, null);
	}

	@Test
	void testDefaultOrderFilesThenConfigServerThenSet() throws IOException {
		Map<String, Object> out = parse(false, false);
		assertEquals("set", out.get("a"), "--set is highest by default");
		assertEquals("cs", out.get("b"), "config-server-only key present");
		assertEquals("csD", out.get("d"), "config server overrides the file by default");
	}

	@Test
	void testOverrideNoneDropsConfigServerBelowFiles() throws IOException {
		Map<String, Object> out = parse(true, false);
		assertEquals("set", out.get("a"), "--set still wins");
		assertEquals("cs", out.get("b"), "config-server-only key still present (lowest, but no competitor)");
		assertEquals("fileD", out.get("d"), "override-none: local file beats config server");
	}

	@Test
	void testOverrideSystemPropertiesLiftsConfigServerAboveSet() throws IOException {
		Map<String, Object> out = parse(false, true);
		assertEquals("cs", out.get("a"), "override-system-properties: config server beats --set");
		assertEquals("csD", out.get("d"), "config server still beats the file");
	}

}
