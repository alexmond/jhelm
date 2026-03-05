package org.alexmond.jhelm.gotemplate.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.Functions;
import org.alexmond.jhelm.gotemplate.TemplateParseException;
import org.alexmond.jhelm.gotemplate.util.StringUtils;

/**
 * Document of go
 * template：<a href="https://pkg.go.dev/text/template#pkg-overview">Template</a>
 */
public class Parser {

	private final Map<String, Function> functions;

	public Parser() {
		this(Functions.GO_BUILTINS);
	}

	public Parser(Map<String, Function> functions) {
		this.functions = functions;
	}

	/**
	 * Parser a template text
	 * @param name the name of template
	 * @param text template content
	 * @return a map containing all ast root nodes
	 * @throws TemplateParseException if reach invalid syntax
	 */
	public Map<String, Node> parse(String name, String text) throws TemplateParseException {
		// Parse the template text, build a list node as the root node
		ListNode listNode = new ListNode();

		Lexer lexer = new Lexer(text);

		State state = new State();
		state.variables.add("$");

		parseList(listNode, lexer, state);

		// Can not have ELSE and END node as the last in root list node
		Node lastNode = listNode.getLast();
		if (lastNode instanceof ElseNode) {
			throwUnexpectError("unexpected " + listNode, state);
		}
		if (lastNode instanceof EndNode) {
			throwUnexpectError("unexpected " + listNode, state);
		}

		ListNode root = (ListNode) state.nodes.get(name);
		if (root != null) {
			for (Node node : root) {
				listNode.append(node);
			}
		}
		else {
			state.nodes.put(name, listNode);
		}

		return state.nodes;
	}

	/**
	 * Parse list node. Must check the last node in the list when this method return
	 * @param listNode List node which contains all nodes in this context
	 * @param lexer Lexer holding tokens
	 * @param state Parser state
	 */
	private void parseList(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		loop: while (true) {
			Token token = moveToNextToken(lexer, state);
			switch (token.type()) {
				case EOF:
					return;
				case TEXT:
					TextNode textNode = new TextNode(token.value());
					listNode.append(textNode);
					break;
				case COMMENT:
					CommentNode commentNode = new CommentNode(token.value());
					listNode.append(commentNode);
					break;
				case LEFT_DELIM:
					token = moveToNextNonSpaceToken(lexer, state);
					if (token == null) {
						throwUnexpectError("unclosed delimiter: " + lexer.getLeftDelimiter(), state);
					}

					if (token.type() == TokenType.DEFINE) {
						parseDefinition(lexer, state);
						continue;
					}

					moveToPrevItem(lexer, state);

					parseAction(listNode, lexer, state);

					// Stop parsing list in current context, keep the last node, let the
					// method caller handles it
					Node lastNode = listNode.getLast();
					if (lastNode instanceof ElseNode) {
						break loop;
					}
					if (lastNode instanceof EndNode) {
						break loop;
					}

					break;
				default:
					throwUnexpectError(String.format("unexpected %s in input", token), state);
			}
		}
	}

	private void parseAction(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing action token", state);
		}

		switch (token.type()) {
			case BLOCK:
				parseBlock(listNode, lexer, state);
				break;
			case BREAK:
				parseBreak(listNode, lexer, state);
				break;
			case CONTINUE:
				parseContinue(listNode, lexer, state);
				break;
			case DEFINE:
				parseDefinition(lexer, state);
				break;
			case ELSE:
				parseElse(listNode, lexer, state);
				break;
			case END:
				parseEnd(listNode, lexer, state);
				break;
			case IF:
				parseIf(listNode, lexer, state);
				break;
			case RANGE:
				parseRange(listNode, lexer, state);
				break;
			case TEMPLATE:
				parseTemplate(listNode, lexer, state);
				break;
			case WITH:
				parseWith(listNode, lexer, state);
				break;
			case COMMENT:
				break;
			default:
				moveToPrevItem(lexer, state);

				// Just action
				ActionNode actionNode = new ActionNode();

				PipeNode pipeNode = new PipeNode("command");
				parsePipe(pipeNode, lexer, TokenType.RIGHT_DELIM, state);
				actionNode.setPipeNode(pipeNode);

				listNode.append(actionNode);
		}
	}

	private void parseBlock(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		String context = "block clause";

		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.STRING && token.type() != TokenType.RAW_STRING) {
			throw new TemplateParseException(String.format("unexpected '%s' in %s", token.value(), context),
					token.line(), token.column());
		}

		String blockTemplateName = StringUtils.unquote(token.value());
		TemplateNode blockTemplateNode = new TemplateNode(blockTemplateName);

		PipeNode pipeNode = new PipeNode(context);
		parsePipe(pipeNode, lexer, TokenType.RIGHT_DELIM, state);
		blockTemplateNode.setPipeNode(pipeNode);

		// Parse block content as an associate template
		ListNode blockListNode = new ListNode();
		parseList(blockListNode, lexer, state);

		Node lastNode = blockListNode.getLast();
		if (lastNode instanceof ElseNode) {
			throwUnexpectError(String.format("unexpected '%s' in block clause", lastNode), state);
		}
		if (lastNode instanceof EndNode) {
			blockListNode.removeLast();
		}

		listNode.append(blockTemplateNode);

		state.nodes.put(blockTemplateName, blockListNode);
	}

	private void parseDefinition(Lexer lexer, State state) throws TemplateParseException {
		String context = "define clause";

		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.STRING && token.type() != TokenType.RAW_STRING) {
			throw new TemplateParseException(String.format("unexpected '%s' in %s", token.value(), context),
					token.line(), token.column());
		}

		String definitionTemplateName = StringUtils.unquote(token.value());

		token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.RIGHT_DELIM) {
			throw new TemplateParseException(String.format("unexpected '%s' in %s", token.value(), context),
					token.line(), token.column());
		}

		ListNode definitionListNode = new ListNode();
		parseList(definitionListNode, lexer, state);

		Node lastNode = definitionListNode.getLast();
		if (lastNode instanceof EndNode) {
			definitionListNode.removeLast();
		}
		else {
			throwUnexpectError(String.format("unexpected '%s' in %s", lastNode, context), state);
			return;
		}

		state.nodes.put(definitionTemplateName, definitionListNode);
	}

	private void parseElse(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		switch (token.type()) {
			case IF:
				moveToPrevItem(lexer, state);
				listNode.append(new ElseNode());
				break;
			case RIGHT_DELIM:
				listNode.append(new ElseNode());
				break;
			default:
				throwUnexpectError(String.format("unexpected %s in end", token), state);
		}
	}

	private void parseBreak(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.RIGHT_DELIM) {
			throwUnexpectError(String.format("unexpected %s in break", token), state);
		}
		listNode.append(new BreakNode());
	}

	private void parseContinue(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.RIGHT_DELIM) {
			throwUnexpectError(String.format("unexpected %s in continue", token), state);
		}
		listNode.append(new ContinueNode());
	}

	private void parseEnd(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.RIGHT_DELIM) {
			throwUnexpectError(String.format("unexpected %s in end", token), state);
		}
		listNode.append(new EndNode());
	}

	private void parseIf(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		moveToNextNonSpaceToken(lexer, state);
		moveToPrevItem(lexer, state);

		IfNode ifNode = new IfNode();
		parseBranch(ifNode, lexer, "if", true, state);
		listNode.append(ifNode);
	}

	private void parseRange(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		moveToNextNonSpaceToken(lexer, state);
		moveToPrevItem(lexer, state);

		RangeNode rangeNode = new RangeNode();
		parseBranch(rangeNode, lexer, "range", true, state);
		listNode.append(rangeNode);
	}

	private void parseTemplate(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		String context = "template clause";

		Token token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.STRING && token.type() != TokenType.RAW_STRING) {
			throw new TemplateParseException(String.format("unexpected '%s' in %s", token.value(), context),
					token.line(), token.column());
		}

		String templateName = StringUtils.unquote(token.value());
		TemplateNode templateNode = new TemplateNode(templateName);

		token = moveToNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() != TokenType.RIGHT_DELIM) {
			moveToPrevItem(lexer, state);

			PipeNode pipeNode = new PipeNode(context);
			parsePipe(pipeNode, lexer, TokenType.RIGHT_DELIM, state);
			templateNode.setPipeNode(pipeNode);
		}

		listNode.append(templateNode);
	}

	private void parseWith(ListNode listNode, Lexer lexer, State state) throws TemplateParseException {
		moveToNextNonSpaceToken(lexer, state);
		moveToPrevItem(lexer, state);

		WithNode withNode = new WithNode();
		parseBranch(withNode, lexer, "with", false, state);
		listNode.append(withNode);
	}

	private void parseBranch(BranchNode branchNode, Lexer lexer, String context, boolean allowElseIf, State state)
			throws TemplateParseException {
		int variableCount = state.variables.size();

		// Parse pipeline, the executable part
		PipeNode pipeNode = new PipeNode(context);
		parsePipe(pipeNode, lexer, TokenType.RIGHT_DELIM, state);
		branchNode.setPipeNode(pipeNode);

		// Parse 'if' clause
		ListNode ifListNode = new ListNode();
		parseList(ifListNode, lexer, state);
		branchNode.setIfListNode(ifListNode);

		// Parse if 'else' clause exists
		ListNode listNode = branchNode.getIfListNode();
		Node lastNode = listNode.getLast();
		if (lastNode instanceof ElseNode) {
			listNode.removeLast();

			if (allowElseIf) {
				Token token = lookNextNonSpaceToken(lexer, state);
				if (token == null) {
					throwUnexpectError("missing token", state);
				}

				if (token.type() == TokenType.IF) {
					moveToNextNonSpaceToken(lexer, state);

					ListNode elseListNode = new ListNode();
					parseIf(elseListNode, lexer, state);
					branchNode.setElseListNode(elseListNode);

					return;
				}
			}

			ListNode elseListNode = new ListNode();
			parseList(elseListNode, lexer, state);
			branchNode.setElseListNode(elseListNode);

			listNode = branchNode.getElseListNode();
		}

		// Check if the branch is closed accurately
		lastNode = listNode.getLast();
		if (lastNode instanceof EndNode) {
			listNode.removeLast();
		}
		else {
			throwUnexpectError("expected end, found " + lastNode, state);
		}

		state.variables.subList(variableCount, state.variables.size()).clear();
	}

	private void parsePipe(PipeNode pipeNode, Lexer lexer, TokenType end, State state) throws TemplateParseException {
		Token token = lookNextNonSpaceToken(lexer, state);
		if (token == null) {
			throwUnexpectError("missing token", state);
		}

		if (token.type() == TokenType.VARIABLE) {
			parseVariable(pipeNode, lexer, state, token);
		}

		while (true) {
			token = moveToNextNonSpaceToken(lexer, state);
			if (token == null) {
				throwUnexpectError("missing token", state);
			}

			if (token.type() == end) {
				List<CommandNode> commands = pipeNode.getCommands();
				if (commands.isEmpty()) {
					throwUnexpectError("missing value for " + pipeNode.getContext(), state);
				}
				for (int i = 1; i < commands.size(); i++) {
					Node firstArgument = commands.get(i).getFirstArgument();
					if (firstArgument instanceof BoolNode) {
						throwUnexpectError(String.format("non executable command in pipeline stage %d", i + 1), state);
					}
					else if (firstArgument instanceof DotNode) {
						throwUnexpectError(String.format("non executable command in pipeline stage %d", i + 1), state);
					}
					else if (firstArgument instanceof NilNode) {
						throwUnexpectError(String.format("non executable command in pipeline stage %d", i + 1), state);
					}
					else if (firstArgument instanceof NumberNode) {
						throwUnexpectError(String.format("non executable command in pipeline stage %d", i + 1), state);
					}
					else if (firstArgument instanceof StringNode) {
						throwUnexpectError(String.format("non executable command in pipeline stage %d", i + 1), state);
					}
				}
				break;
			}
			switch (token.type()) {
				case BOOL:
				case CHAR_CONSTANT:
				case COMPLEX:
				case DOT:
				case FIELD:
				case IDENTIFIER:
				case NUMBER:
				case NIL:
				case RAW_STRING:
				case STRING:
				case VARIABLE:
				case LEFT_PAREN:
					moveToPrevItem(lexer, state);
					parseCommand(pipeNode, lexer, state);
					break;
				case PIPE:
					// Continue to next command in pipeline
					break;
				case ERROR:
				default:
					throw new TemplateParseException(String.format("unexpected %s in %s", token, pipeNode.getContext()),
							token.line(), token.column());
			}
		}
	}

	private void parseVariable(PipeNode pipeNode, Lexer lexer, State state, Token variableToken)
			throws TemplateParseException {
		moveToNextNonSpaceToken(lexer, state);
		Token nextToken = lookNextNonSpaceToken(lexer, state);
		if (nextToken == null) {
			throwUnexpectError("missing token", state);
		}

		switch (nextToken.type()) {
			case ASSIGN:
			case DECLARE:
				moveToNextNonSpaceToken(lexer, state);
				pipeNode.append(new VariableNode(variableToken.value()));
				state.variables.add(variableToken.value());
				break;
			case CHAR:
				if (",".equals(nextToken.value())) {
					moveToNextNonSpaceToken(lexer, state);
					pipeNode.append(new VariableNode(variableToken.value()));
					state.variables.add(variableToken.value());
					if ("range".equals(pipeNode.getContext()) && pipeNode.getVariableCount() < 2) {
						nextToken = lookNextNonSpaceToken(lexer, state);
						if (nextToken == null) {
							throwUnexpectError("missing token", state);
						}

						switch (nextToken.type()) {
							case VARIABLE:
							case RIGHT_DELIM:
							case RIGHT_PAREN:
								if (variableToken.type() == TokenType.VARIABLE) {
									parseVariable(pipeNode, lexer, state, nextToken);
								}
								break;
							default:
								throwUnexpectError("", state);
						}
					}
				}
				break;
			default:
				moveToPrevNonSpaceItem(lexer, state);
				break;
		}
	}

	private void parseCommand(PipeNode pipeNode, Lexer lexer, State state) throws TemplateParseException {
		CommandNode commandNode = new CommandNode();

		loop: while (true) {
			Node node = null;

			Token token = moveToNextNonSpaceToken(lexer, state);
			if (token == null) {
				throwUnexpectError("missing token", state);
			}

			switch (token.type()) {
				case IDENTIFIER:
					String name = token.value();
					if (!hasFunction(name)) {
						throwUnexpectError(String.format("function %s not defined", token.value()), state);
					}
					node = new IdentifierNode(token.value());
					break;
				case DOT:
					node = new DotNode();
					break;
				case NIL:
					node = new NilNode();
					break;
				case VARIABLE:
					node = findVariable(token.value(), state);
					break;
				case FIELD:
					node = new FieldNode(token.value());
					break;
				case BOOL:
					node = new BoolNode(token.value());
					break;
				case CHAR_CONSTANT:
				case COMPLEX:
				case NUMBER:
					node = NumberParser.parse(token);
					break;
				case STRING:
				case RAW_STRING:
					node = new StringNode(token.value());
					break;
				case LEFT_PAREN:
					PipeNode nestedPipeNode = new PipeNode("parenthesized pipeline");
					parsePipe(nestedPipeNode, lexer, TokenType.RIGHT_PAREN, state);
					node = nestedPipeNode;
					break;
				case CHAR:
					if ("-".equals(token.val()) || "+".equals(token.val())) {
						Token nextToken = lookNextItem(lexer, state);
						if (nextToken != null
								&& (nextToken.type() == TokenType.NUMBER || nextToken.type() == TokenType.DOT)) {
							node = NumberParser.parse(token);
							break;
						}
					}
					moveToPrevItem(lexer, state);
					break loop;
				case PIPE:
				case RIGHT_DELIM:
				case RIGHT_PAREN:
				case EOF:
					moveToPrevItem(lexer, state);
					break loop;
				default:
					throwUnexpectError("unexpected token: " + token.type(), state);
					break;
			}

			if (node != null) {
				token = lookNextItem(lexer, state);
				if (token != null && token.type() == TokenType.FIELD) {
					// Validate that the node can have fields applied to it
					if (node instanceof BoolNode) {
						throwUnexpectError("unexpected . after boolean", state);
					}
					else if (node instanceof NumberNode) {
						throwUnexpectError("unexpected . after number", state);
					}
					else if (node instanceof StringNode) {
						throwUnexpectError("unexpected . after string", state);
					}
					else if (node instanceof NilNode) {
						throwUnexpectError("unexpected . after nil", state);
					}
					else if (node instanceof DotNode) {
						throwUnexpectError("unexpected . after .", state);
					}

					ChainNode chainNode = new ChainNode(node);
					while (true) {
						token = lookNextItem(lexer, state);
						if (token != null && token.type() == TokenType.FIELD) {
							token = moveToNextToken(lexer, state);
							chainNode.append(token.value());
						}
						else {
							break;
						}
					}
					node = chainNode;
				}
				commandNode.append(node);
			}

			token = moveToNextToken(lexer, state);
			if (token == null) {
				break;
			}
			switch (token.type()) {
				case SPACE:
					continue loop;
				case RIGHT_DELIM:
				case RIGHT_PAREN:
					moveToPrevItem(lexer, state);
					break loop;
				case PIPE:
					moveToPrevItem(lexer, state);
					break loop;
				default:
					throwUnexpectError(String.format("unexpected %s in operand", token), state);
			}
		}

		if (commandNode.getArgumentCount() == 0) {
			throwUnexpectError("empty command", state);
		}

		pipeNode.append(commandNode);
	}

	private boolean hasFunction(String name) {
		return functions.containsKey(name);
	}

	/**
	 * Look at the next token without advancing the position marker
	 * @return The next token. First call returns the first token, returns null after the
	 * last token
	 */
	private Token lookNextItem(Lexer lexer, State state) {
		if (state.tokenIndex < lexer.getTokens().size()) {
			return lexer.getTokens().get(state.tokenIndex);
		}
		return null;
	}

	/**
	 * Retrieve previous token.
	 * <p>
	 * If the index is 0 (never run {@code moveToNextToken()}), then it won't change, this
	 * method will return null. If the index points to the last token, then return the
	 * last to
	 */
	private Token moveToPrevItem(Lexer lexer, State state) {
		if (state.tokenIndex > 0) {
			return lexer.getTokens().get(--state.tokenIndex);
		}
		return null;
	}

	/**
	 * Retrieve next token, and move the index to the next token.
	 * <p>
	 * If current index points to the last token, then it won't change, this method will
	 * return null
	 */
	private Token moveToNextToken(Lexer lexer, State state) {
		Token token = lookNextItem(lexer, state);
		if (token != null) {
			state.tokenIndex++;
			state.lastToken = token;
		}
		return token;
	}

	/**
	 * Look at the next non-space token without advancing the position marker
	 * @return The next non-space token. First call returns the first non-space token,
	 * returns null after the last token
	 */
	private Token lookNextNonSpaceToken(Lexer lexer, State state) {
		int count = 0;
		while (true) {
			Token token = moveToNextToken(lexer, state);
			count++;
			if (token == null) {
				return null;
			}
			if (token.type() != TokenType.SPACE) {
				state.tokenIndex -= count;
				return token;
			}
		}
	}

	/**
	 * Get the previous non-space token
	 * @return The previous non-space token. First call returns null, after the last token
	 * returns the last token
	 */
	private Token moveToPrevNonSpaceItem(Lexer lexer, State state) {
		while (true) {
			Token token = moveToPrevItem(lexer, state);
			if (token == null) {
				return null;
			}
			if (token.type() != TokenType.SPACE) {
				return token;
			}
		}
	}

	/**
	 * Get the next non-space token and advance the position marker past it
	 * @return The next non-space token. First call returns the first non-space token,
	 * returns null after the last token
	 */
	private Token moveToNextNonSpaceToken(Lexer lexer, State state) {
		while (true) {
			Token token = moveToNextToken(lexer, state);
			if (token == null) {
				return null;
			}
			if (token.type() != TokenType.SPACE) {
				return token;
			}
		}
	}

	private Node findVariable(String value, State state) throws TemplateParseException {
		VariableNode variableNode = new VariableNode(value);
		String name = variableNode.getIdentifier(0);
		if (state.variables.contains(name)) {
			return variableNode;
		}
		throwUnexpectError(String.format("undefined variable %s", name), state);
		return null;
	}

	private void throwUnexpectError(String message, State state) throws TemplateParseException {
		Token token = state.lastToken;
		if (token != null && token.line() > 0) {
			throw new TemplateParseException(message, token.line(), token.column());
		}
		throw new TemplateParseException(message);
	}

	private static final class State {

		private final Map<String, Node> nodes = new LinkedHashMap<>();

		/**
		 * A list which contains all the variables in a branch context
		 */
		private final List<String> variables = new ArrayList<>();

		/**
		 * Position marker
		 */
		private int tokenIndex;

		/**
		 * Last token consumed — used for error location reporting.
		 */
		private Token lastToken;

	}

}
