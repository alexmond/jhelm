package org.alexmond.jhelm.core.service;

import org.alexmond.jhelm.core.exception.JhelmException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalCommandPostRendererTest {

	@TempDir
	File tempDir;

	@Test
	void processPassesManifestThroughCommand() throws Exception {
		// 'cat' passes stdin to stdout unchanged
		ExternalCommandPostRenderer renderer = new ExternalCommandPostRenderer(List.of("cat"));
		String input = "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test\n";
		String result = renderer.process(input);
		assertEquals(input, result);
	}

	@Test
	void processTransformsManifest() throws Exception {
		// Use sed to add a label
		ExternalCommandPostRenderer renderer = new ExternalCommandPostRenderer(
				List.of("sed", "s/name: test/name: transformed/"));
		String input = "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test\n";
		String result = renderer.process(input);
		assertTrue(result.contains("name: transformed"));
	}

	@Test
	void processChainMultipleRenderers() throws Exception {
		String input = "original";
		ExternalCommandPostRenderer first = new ExternalCommandPostRenderer(List.of("sed", "s/original/step1/"));
		ExternalCommandPostRenderer second = new ExternalCommandPostRenderer(List.of("sed", "s/step1/step2/"));
		String result = second.process(first.process(input));
		assertTrue(result.trim().equals("step2"));
	}

	@Test
	void processThrowsOnNonZeroExitCode() throws Exception {
		ExternalCommandPostRenderer renderer = new ExternalCommandPostRenderer(List.of("false"));
		assertThrows(IOException.class, () -> renderer.process("input"));
	}

	@Test
	void processIncludesStderrInErrorOnNonZeroExit() throws Exception {
		// a non-zero exit that also writes to stderr — the error message folds the
		// captured stderr in (the stderr-non-blank branch)
		ExternalCommandPostRenderer renderer = new ExternalCommandPostRenderer(
				List.of("bash", "-c", "echo 'boom happened' >&2; exit 2"));
		IOException ex = assertThrows(IOException.class, () -> renderer.process("input"));
		assertTrue(ex.getMessage().contains("boom happened"));
		assertTrue(ex.getMessage().contains("code 2"));
	}

	@Test
	void processThrowsOnTimeout() throws Exception {
		// Use bash -c to read stdin first (so the process doesn't exit when stdin closes)
		// then sleep
		ExternalCommandPostRenderer renderer = new ExternalCommandPostRenderer(
				List.of("bash", "-c", "cat > /dev/null && sleep 30"), 1);
		assertThrows(IOException.class, () -> renderer.process("input"));
	}

	@Test
	void processWithScriptPostRenderer() throws Exception {
		File script = new File(tempDir, "post-render.sh");
		Files.writeString(script.toPath(), """
				#!/bin/sh
				sed 's/replicas: 1/replicas: 3/'
				""");
		script.setExecutable(true);

		ExternalCommandPostRenderer renderer = new ExternalCommandPostRenderer(List.of(script.getAbsolutePath()));
		String input = "apiVersion: apps/v1\nkind: Deployment\nspec:\n  replicas: 1\n";
		String result = renderer.process(input);
		assertTrue(result.contains("replicas: 3"));
	}

	@Test
	void processReInterruptsWhenThreadInterrupted() {
		// A long-running command keeps the process alive so waitFor() is still blocking
		// when
		// it observes the pre-set interrupt and throws InterruptedException.
		ExternalCommandPostRenderer renderer = new ExternalCommandPostRenderer(List.of("sleep", "10"));
		Thread.currentThread().interrupt();
		JhelmException ex = assertThrows(JhelmException.class, () -> renderer.process("data: x\n"));
		// The interrupt flag must be restored (cleared here so it does not leak to other
		// tests).
		assertTrue(Thread.interrupted(), "interrupt flag should be restored");
		assertTrue(ex.getMessage().contains("Interrupted"), ex.getMessage());
	}

}
