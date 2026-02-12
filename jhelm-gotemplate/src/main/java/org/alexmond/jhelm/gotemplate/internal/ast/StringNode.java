package org.alexmond.jhelm.gotemplate.internal.ast;

import org.alexmond.jhelm.gotemplate.internal.lang.StringUtils;

public class StringNode implements Node {

    private final String origin;
    private final String text;

    public StringNode(String origin) {
        this.origin = origin;
        this.text = StringUtils.unquote(origin);
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return origin;
    }
}
