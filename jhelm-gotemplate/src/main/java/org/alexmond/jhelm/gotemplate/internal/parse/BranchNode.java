package org.alexmond.jhelm.gotemplate.internal.parse;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BranchNode implements Node {

    private PipeNode pipeNode;
    private ListNode ifListNode;
    private ListNode elseListNode;

    @Override
    public String toString() {
        String name = pipeNode.getContext();
        switch (name) {
            case "if":
            case "range":
            case "with":
                break;
            default:
                throw new IllegalStateException("unknown branch type");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{{").append(name).append(' ').append(pipeNode).append("}}");
        if (ifListNode != null) {
            // Avoid printing "null"
            sb.append(ifListNode);
        }
        if (elseListNode != null) {
            sb.append("{{else}}").append(elseListNode);
        }
        sb.append("{{end}}");
        return sb.toString();
    }
}
