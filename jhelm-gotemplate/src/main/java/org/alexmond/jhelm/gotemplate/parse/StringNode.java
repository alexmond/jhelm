package org.alexmond.jhelm.gotemplate.parse;

import lombok.Getter;
import org.alexmond.jhelm.gotemplate.util.StringUtils;

@Getter
public class StringNode implements Node {

	private final String origin;

	private final String text;

	public StringNode(String origin) {
		this.origin = origin;
		this.text = StringUtils.unquote(origin);
	}

	@Override
	public String toString() {
		return origin;
	}

}
