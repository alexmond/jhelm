package org.alexmond.jhelm.gotemplate.internal.parse;

import lombok.Getter;
import lombok.Setter;
import org.alexmond.jhelm.gotemplate.internal.util.StringUtils;

@Getter
@Setter
public class TemplateNode implements Node {

    private final String name;

    private PipeNode pipeNode;

    public TemplateNode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{{template ").append(StringUtils.quote(name));
        if (pipeNode != null) {
            sb.append(' ').append(pipeNode);
        }
        sb.append("}}");
        return sb.toString();
    }
}
