package org.alexmond.gotmpl4j.exec;

/**
 * Signal thrown when a {@code {{continue}}} is encountered inside a range loop.
 */
class ContinueSignal extends RuntimeException {

	ContinueSignal() {
		super(null, null, true, false);
	}

}
