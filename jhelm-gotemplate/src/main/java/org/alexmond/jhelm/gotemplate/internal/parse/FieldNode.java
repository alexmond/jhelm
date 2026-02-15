package org.alexmond.jhelm.gotemplate.internal.parse;

import lombok.Getter;

@Getter
public class FieldNode implements Node {

    private final String[] identifiers;

    public FieldNode(String value) {
        this.identifiers = value.substring(1).split("\\.");
    }

    @Override
    public String toString() {
        return "." + String.join(".", identifiers);
    }
}
