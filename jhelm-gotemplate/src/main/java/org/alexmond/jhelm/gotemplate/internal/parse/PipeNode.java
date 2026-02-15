package org.alexmond.jhelm.gotemplate.internal.parse;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
public class PipeNode implements Node {

    private final String context;
    private final List<VariableNode> variables = new LinkedList<>();
    private final List<CommandNode> commands = new LinkedList<>();

    public PipeNode(String context) {
        this.context = context;
    }

    public void append(VariableNode variableNode) {
        variables.add(variableNode);
    }

    public int getVariableCount() {
        return variables.size();
    }

    public void append(CommandNode commandNode) {
        commands.add(commandNode);
    }

    @Override
    public String toString() {
        String variableString = !variables.isEmpty() ?
                variables.stream().map(Objects::toString).collect(Collectors.joining(", ")) + " := " :
                "";
        return variableString + commands.stream().map(Objects::toString).collect(Collectors.joining(" | "));
    }
}
