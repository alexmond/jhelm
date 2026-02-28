package org.alexmond.jhelm.core.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link PostRenderProcessor} that pipes the rendered manifest through an external
 * command via stdin/stdout. This mirrors the Helm {@code --post-renderer} behavior.
 *
 * <p>
 * The command receives the rendered manifest on standard input and is expected to produce
 * the transformed manifest on standard output. If the command exits with a non-zero
 * status, an exception is thrown.
 */
@Slf4j
public class ExternalCommandPostRenderer implements PostRenderProcessor {

	private final List<String> command;

	private final long timeoutSeconds;

	/**
	 * Create a post-renderer that invokes the given command.
	 * @param command the command and its arguments
	 */
	public ExternalCommandPostRenderer(List<String> command) {
		this(command, 300);
	}

	/**
	 * Create a post-renderer with a custom timeout.
	 * @param command the command and its arguments
	 * @param timeoutSeconds maximum time to wait for the command
	 */
	public ExternalCommandPostRenderer(List<String> command, long timeoutSeconds) {
		this.command = command;
		this.timeoutSeconds = timeoutSeconds;
	}

	@Override
	public String process(String renderedManifest) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(false);

		if (log.isDebugEnabled()) {
			log.debug("Running post-renderer: {}", String.join(" ", command));
		}

		Process process = pb.start();
		try {
			// Write stdin in a separate thread to avoid deadlock with stdout/stderr
			CompletableFuture<Void> stdinFuture = CompletableFuture.runAsync(() -> {
				try (OutputStream stdin = process.getOutputStream()) {
					stdin.write(renderedManifest.getBytes(StandardCharsets.UTF_8));
				}
				catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			});

			// Read stdout and stderr concurrently
			CompletableFuture<String> stdoutFuture = CompletableFuture
				.supplyAsync(() -> readStreamUnchecked(process.getInputStream()));
			CompletableFuture<String> stderrFuture = CompletableFuture
				.supplyAsync(() -> readStreamUnchecked(process.getErrorStream()));

			boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new IOException(
						"Post-renderer command timed out after " + timeoutSeconds + "s: " + String.join(" ", command));
			}

			// Stdin may fail if process exits before input is fully written (e.g.,
			// broken pipe). Defer this error so we can check exit code first.
			CompletionException stdinError = null;
			try {
				stdinFuture.join();
			}
			catch (CompletionException ex) {
				stdinError = ex;
			}
			String stdout = stdoutFuture.join();
			String stderr = stderrFuture.join();

			int exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new IOException("Post-renderer command exited with code " + exitCode + ": "
						+ String.join(" ", command) + (stderr.isBlank() ? "" : "\nstderr: " + stderr));
			}
			if (stdinError != null) {
				throw stdinError;
			}

			if (log.isDebugEnabled()) {
				log.debug("Post-renderer completed successfully");
			}
			return stdout;
		}
		finally {
			process.destroyForcibly();
		}
	}

	private String readStreamUnchecked(InputStream stream) {
		try (stream) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = stream.read(buffer)) != -1) {
				bos.write(buffer, 0, read);
			}
			return bos.toString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

}
