package org.alexmond.jhelm.gotemplate.internal.ast;

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
