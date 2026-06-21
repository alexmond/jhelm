package org.alexmond.gotmpl4j;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Caches a compiled {@link GoTemplate} set so it is built once and reused across renders.
 *
 * <p>
 * Compiling (parsing) a template is the expensive step; a {@link GoTemplate} is immutable
 * once parsed and {@code execute} builds a fresh executor per call, so a single compiled
 * instance is safe to share across threads. This holder owns that "compile once, render
 * many" lifecycle: the supplied {@code compiler} performs the compile, and the result is
 * memoised with double-checked locking.
 *
 * <p>
 * Caching can be disabled (e.g. for development, so template edits are picked up without
 * a restart) — {@link #get()} then re-compiles on every call. Use {@link #invalidate()}
 * to drop the cached instance and force a re-compile on the next {@link #get()}.
 *
 * <p>
 * The {@code compiler} is intentionally a plain {@link Supplier}: how the template
 * sources are located and parsed (classpath resources, files, an in-memory string, ...)
 * is left to the caller, keeping this engine class free of any loading or framework
 * concerns.
 */
public class TemplateCache {

	private final Supplier<GoTemplate> compiler;

	private final boolean enabled;

	private final ReentrantLock lock = new ReentrantLock();

	private volatile GoTemplate cached;

	/**
	 * Creates a caching holder (caching enabled).
	 * @param compiler compiles (parses) the template set on demand; must not be
	 * {@code null}
	 */
	public TemplateCache(Supplier<GoTemplate> compiler) {
		this(compiler, true);
	}

	/**
	 * Creates a holder.
	 * @param compiler compiles (parses) the template set on demand; must not be
	 * {@code null}
	 * @param enabled whether to cache the compiled result; when {@code false} every
	 * {@link #get()} re-compiles
	 */
	public TemplateCache(Supplier<GoTemplate> compiler, boolean enabled) {
		if (compiler == null) {
			throw new IllegalArgumentException("compiler must not be null");
		}
		this.compiler = compiler;
		this.enabled = enabled;
	}

	/**
	 * Returns the compiled template set, building it once and reusing it when caching is
	 * enabled, or re-compiling on every call when disabled.
	 * @return the compiled template set
	 */
	public GoTemplate get() {
		if (!this.enabled) {
			return this.compiler.get();
		}
		GoTemplate result = this.cached;
		if (result == null) {
			this.lock.lock();
			try {
				result = this.cached;
				if (result == null) {
					result = this.compiler.get();
					this.cached = result;
				}
			}
			finally {
				this.lock.unlock();
			}
		}
		return result;
	}

	/**
	 * Drops the cached compiled template set so the next {@link #get()} re-compiles. A
	 * no-op when caching is disabled.
	 */
	public void invalidate() {
		this.cached = null;
	}

	/**
	 * Whether caching is enabled.
	 * @return {@code true} if compiled results are cached, {@code false} if every
	 * {@link #get()} re-compiles
	 */
	public boolean isEnabled() {
		return this.enabled;
	}

}
