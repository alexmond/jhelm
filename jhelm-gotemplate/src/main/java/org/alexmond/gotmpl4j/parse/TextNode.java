package org.alexmond.gotmpl4j.parse;

import lombok.Data;

@Data
public class TextNode implements Node {

	private final String text;

	@Override
	public String toString() {
		return '"' + text + '"';
	}

}
