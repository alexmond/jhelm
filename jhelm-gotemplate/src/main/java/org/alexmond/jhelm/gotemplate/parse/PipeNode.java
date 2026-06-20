package org.alexmond.jhelm.gotemplate.parse;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public class PipeNode implements Node {

	private final String context;

	private final List<VariableNode> variables = new LinkedList<>();

	private final List<CommandNode> commands = new LinkedList<>();

	// true for a `:=` declaration (and range vars), false for a `=` assignment. A
	// declaration is scoped to its enclosing block; an assignment updates the existing
	// variable and persists past the block (Go text/template semantics).
	private boolean declare = true;

	public PipeNode(String context) {
		this.context = context;
	}

	public void setDeclare(boolean declare) {
		this.declare = declare;
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
		String variableString = !variables.isEmpty()
				? variables.stream().map(Objects::toString).collect(Collectors.joining(", ")) + " := " : "";
		return variableString + commands.stream().map(Objects::toString).collect(Collectors.joining(" | "));
	}

}
