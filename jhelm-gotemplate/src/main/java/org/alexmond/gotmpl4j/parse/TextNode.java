package org.alexmond.gotmpl4j.parse;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextNode implements Node {

	// Mutable so the html/template escape pass can rewrite literal text (e.g. a stray '<'
	// in HTML text node content becomes "&lt;").
	private String text;

	public TextNode(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return '"' + text + '"';
	}

}
