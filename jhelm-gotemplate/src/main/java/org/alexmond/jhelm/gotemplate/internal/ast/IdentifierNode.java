package org.alexmond.jhelm.gotemplate.internal.ast;

import lombok.Data;

@Data
public class IdentifierNode implements Node {

    private final String identifier;

    @Override
    public String toString() {
        return identifier;
    }
}
