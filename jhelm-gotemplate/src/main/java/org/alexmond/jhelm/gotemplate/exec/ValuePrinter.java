package org.alexmond.jhelm.gotemplate.exec;

import java.io.IOException;
import java.io.Writer;

import org.alexmond.jhelm.gotemplate.GoFmt;

final class ValuePrinter {

	private ValuePrinter() {
	}

	/**
	 * Writes a value to output the way Go's {@code fmt.Sprint} would: a values
	 * {@code float64} uses Go's %g ({@code 1000000 -> 1e+06}), a map renders as
	 * {@code map[k:v …]} with sorted keys, a slice as {@code [a b c]}. A {@code null}
	 * renders nothing (Go's text/template prints an absent action as empty here).
	 */
	static void printValue(Writer writer, Object value) throws IOException {
		if (value == null) {
			return;
		}
		writer.write(GoFmt.sprint(value));
	}

}
