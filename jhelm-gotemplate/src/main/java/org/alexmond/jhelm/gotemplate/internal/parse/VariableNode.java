package org.alexmond.jhelm.gotemplate.internal.parse;

import lombok.Getter;

@Getter
public class VariableNode implements Node {

    private final String[] identifiers;

    public VariableNode(String value) {
        this.identifiers = value.split("\\.");
    }

    public String getIdentifier(int index) {
        return identifiers[index];
    }

    @Override
    public String toString() {
        return String.join(".", identifiers);
    }
}
