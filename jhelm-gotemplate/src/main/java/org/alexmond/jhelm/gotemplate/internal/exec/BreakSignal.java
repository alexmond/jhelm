package org.alexmond.jhelm.gotemplate.internal.exec;

/**
 * Signal thrown when a {@code {{break}}} is encountered inside a range loop.
 */
class BreakSignal extends RuntimeException {

	BreakSignal() {
		super(null, null, true, false);
	}

}
