package org.alexmond.gotmpl4j.parse;

import lombok.Getter;

@Getter
public class BoolNode implements Node {

	private final boolean value;

	public BoolNode(String text) {
		this.value = Boolean.parseBoolean(text);
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}

}
