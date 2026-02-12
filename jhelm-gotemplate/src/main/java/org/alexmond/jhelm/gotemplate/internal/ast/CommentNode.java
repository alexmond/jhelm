package org.alexmond.jhelm.gotemplate.internal.ast;

import lombok.Getter;

@Getter
public class CommentNode implements Node {

    private final String comment;

    public CommentNode(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return comment;
    }
}
