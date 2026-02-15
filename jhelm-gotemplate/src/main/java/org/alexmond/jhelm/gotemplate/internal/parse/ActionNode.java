package org.alexmond.jhelm.gotemplate.internal.parse;

import lombok.Data;

@Data
public class ActionNode implements Node {

    private PipeNode pipeNode;

    @Override
    public String toString() {
        String pipe = pipeNode != null ? pipeNode.toString() : "";
        return "{{" + pipe + "}}";
    }
}
