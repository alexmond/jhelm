package org.alexmond.gotmpl4j.parse;

import lombok.Data;

@Data
public class IdentifierNode implements Node {

	private final String identifier;

	@Override
	public String toString() {
		return identifier;
	}

}
