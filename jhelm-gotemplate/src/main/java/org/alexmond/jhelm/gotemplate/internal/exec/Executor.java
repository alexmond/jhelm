package org.alexmond.jhelm.gotemplate.internal.exec;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.Functions;
import org.alexmond.jhelm.gotemplate.TemplateExecutionException;
import org.alexmond.jhelm.gotemplate.TemplateNotFoundException;
import org.alexmond.jhelm.gotemplate.internal.parse.ActionNode;
import org.alexmond.jhelm.gotemplate.internal.parse.BoolNode;
import org.alexmond.jhelm.gotemplate.internal.parse.BreakNode;
import org.alexmond.jhelm.gotemplate.internal.parse.ChainNode;
import org.alexmond.jhelm.gotemplate.internal.parse.CommandNode;
import org.alexmond.jhelm.gotemplate.internal.parse.CommentNode;
import org.alexmond.jhelm.gotemplate.internal.parse.ContinueNode;
import org.alexmond.jhelm.gotemplate.internal.parse.DotNode;
import org.alexmond.jhelm.gotemplate.internal.parse.FieldNode;
import org.alexmond.jhelm.gotemplate.internal.parse.IdentifierNode;
import org.alexmond.jhelm.gotemplate.internal.parse.IfNode;
import org.alexmond.jhelm.gotemplate.internal.parse.ListNode;
import org.alexmond.jhelm.gotemplate.internal.parse.NilNode;
import org.alexmond.jhelm.gotemplate.internal.parse.Node;
import org.alexmond.jhelm.gotemplate.internal.parse.NumberNode;
import org.alexmond.jhelm.gotemplate.internal.parse.PipeNode;
import org.alexmond.jhelm.gotemplate.internal.parse.RangeNode;
import org.alexmond.jhelm.gotemplate.internal.parse.StringNode;
import org.alexmond.jhelm.gotemplate.internal.parse.TemplateNode;
import org.alexmond.jhelm.gotemplate.internal.parse.TextNode;
import org.alexmond.jhelm.gotemplate.internal.parse.VariableNode;
import org.alexmond.jhelm.gotemplate.internal.parse.WithNode;

@Slf4j
public class Executor {

	private final Map<String, Node> rootNodes;

	private final Map<String, Function> functions;

	// BeanInfo cache for performance optimization
	private final Map<Class<?>, BeanInfo> beanInfoCache = new ConcurrentHashMap<>();

	// Variable storage for template execution context
	private final Map<String, Object> variables = new HashMap<>();

	// Root data for $ variable
	private Object rootData;

	public Executor(Map<String, Node> rootNodes, Map<String, Function> functions) {
		this.rootNodes = rootNodes;
		this.functions = functions;
	}

	public void execute(String name, Object data, Writer writer)
			throws IOException, TemplateNotFoundException, TemplateExecutionException {
		Node node = rootNodes.get(name);
		if (node == null) {
			throw new TemplateNotFoundException(String.format("template '%s' not found", name));
		}

		if (node instanceof ListNode listNode) {
			try {
				// Store root data for $ variable
				this.rootData = data;
				variables.put("$", data);
				BeanInfo beanInfo = getBeanInfo(data);
				writeNode(writer, listNode, data, beanInfo);
			}
			catch (IndexOutOfBoundsException ex) {
				if (log.isDebugEnabled()) {
					log.debug("Internal IndexOutOfBounds in '{}': {}", name, ex.getMessage(), ex);
				}
				throw new TemplateExecutionException("Internal IndexOutOfBounds in '" + name + "': " + ex.getMessage(),
						ex);
			}
			catch (Exception ex) {
				if (ex instanceof IOException || ex instanceof TemplateExecutionException
						|| ex instanceof TemplateNotFoundException) {
					throw ex;
				}
				if (log.isDebugEnabled()) {
					log.debug("Execution failure in template '{}': {}", name, ex.getMessage(), ex);
				}
				throw new TemplateExecutionException("Execution error in '" + name + "': " + ex.getMessage(), ex);
			}
		}
		else {
			throw new TemplateExecutionException(String.format("root node for '%s' is not a ListNode", name));
		}
	}

	private void writeNode(Writer writer, Node node, Object data, BeanInfo beanInfo)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		if (node == null) {
			return;
		}
		if (node instanceof ListNode) {
			writeList(writer, (ListNode) node, data, beanInfo);
		}
		else if (node instanceof ActionNode actionNode) {
			writeAction(writer, actionNode, data, beanInfo);
		}
		else if (node instanceof CommentNode commentNode) {
			if (log.isTraceEnabled()) {
				log.trace("Skipping comment node: {}", commentNode);
			}
		}
		else if (node instanceof IfNode ifNode) {
			writeIf(writer, ifNode, data, beanInfo);
		}
		else if (node instanceof RangeNode rangeNode) {
			writeRange(writer, rangeNode, data, beanInfo);
		}
		else if (node instanceof TemplateNode templateNode) {
			writeTemplate(writer, templateNode, data, beanInfo);
		}
		else if (node instanceof TextNode textNode) {
			writeText(writer, textNode);
		}
		else if (node instanceof WithNode withNode) {
			writeWith(writer, withNode, data, beanInfo);
		}
		else if (node instanceof BreakNode) {
			throw new BreakSignal();
		}
		else if (node instanceof ContinueNode) {
			throw new ContinueSignal();
		}
		else {
			throw new TemplateExecutionException(String.format("unknown node: %s", node.toString()));
		}
	}

	private void writeAction(Writer writer, ActionNode actionNode, Object data, BeanInfo beanInfo)
			throws IOException, TemplateExecutionException {
		PipeNode pipeNode = actionNode.getPipeNode();
		Object value = executePipe(pipeNode, data, beanInfo);

		// Debug: log variable assignments
		if (pipeNode.getVariableCount() > 0) {
			for (VariableNode variable : pipeNode.getVariables()) {
				String varName = variable.getIdentifier(0);
				if (log.isDebugEnabled()) {
					log.debug("Action assigned variable: {} = {}", varName, value);
				}
			}
		}

		if (pipeNode.getVariableCount() == 0) {
			printValue(writer, value);
		}
	}

	private void writeIf(Writer writer, IfNode ifNode, Object data, BeanInfo beanInfo)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		Object value = executePipe(ifNode.getPipeNode(), data, beanInfo);
		if (isTrue(value)) {
			writeNode(writer, ifNode.getIfListNode(), data, beanInfo);
		}
		else if (ifNode.getElseListNode() != null) {
			writeNode(writer, ifNode.getElseListNode(), data, beanInfo);
		}
	}

	private void writeList(Writer writer, ListNode listNode, Object data, BeanInfo beanInfo)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		for (Node node : listNode) {
			writeNode(writer, node, data, beanInfo);
		}
	}

	private void writeRange(Writer writer, RangeNode rangeNode, Object data, BeanInfo beanInfo)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		PipeNode pipeNode = rangeNode.getPipeNode();
		List<VariableNode> rangeVars = pipeNode.getVariables();

		// Save current variable values to restore later
		Map<String, Object> savedVars = new HashMap<>();
		for (VariableNode varNode : rangeVars) {
			String varName = varNode.getIdentifier(0);
			if (variables.containsKey(varName)) {
				savedVars.put(varName, variables.get(varName));
			}
		}

		// Initialize range variables to null in case the range is empty
		for (VariableNode varNode : rangeVars) {
			variables.put(varNode.getIdentifier(0), null);
		}

		// Execute pipe but don't set variables yet - we'll set them per iteration
		Object arrayOrList = executePipeWithoutVariableAssignment(pipeNode, data, beanInfo);

		if (arrayOrList == null) {
			if (rangeNode.getElseListNode() != null) {
				writeNode(writer, rangeNode.getElseListNode(), data, beanInfo);
			}
			restoreVariables(rangeVars, savedVars);
			return;
		}

		iterateRange(writer, rangeNode, rangeVars, arrayOrList, data, beanInfo, savedVars);

		// Restore variables after range
		restoreVariables(rangeVars, savedVars);
	}

	private void iterateRange(Writer writer, RangeNode rangeNode, List<VariableNode> rangeVars, Object arrayOrList,
			Object data, BeanInfo beanInfo, Map<String, Object> savedVars)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		if (arrayOrList.getClass().isArray()) {
			int length = Array.getLength(arrayOrList);
			if (length == 0 && rangeNode.getElseListNode() != null) {
				writeNode(writer, rangeNode.getElseListNode(), data, beanInfo);
				restoreVariables(rangeVars, savedVars);
				return;
			}
			for (int i = 0; i < length; i++) {
				Object value = Array.get(arrayOrList, i);
				setRangeVariables(rangeVars, i, value);
				if (writeRangeItem(writer, rangeNode, value)) {
					return;
				}
			}
		}
		else if (arrayOrList instanceof Collection<?> collection) {
			iterateCollection(writer, rangeNode, rangeVars, collection, data, beanInfo, savedVars);
		}
		else if (arrayOrList instanceof Map<?, ?> map) {
			iterateMap(writer, rangeNode, rangeVars, map, data, beanInfo, savedVars);
		}
		else {
			restoreVariables(rangeVars, savedVars);
			throw new TemplateExecutionException(
					String.format("can't iterate over %s", arrayOrList.getClass().getName()));
		}
	}

	private void iterateCollection(Writer writer, RangeNode rangeNode, List<VariableNode> rangeVars,
			Collection<?> collection, Object data, BeanInfo beanInfo, Map<String, Object> savedVars)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		if (collection.isEmpty() && rangeNode.getElseListNode() != null) {
			writeNode(writer, rangeNode.getElseListNode(), data, beanInfo);
			restoreVariables(rangeVars, savedVars);
			return;
		}
		int index = 0;
		for (Object object : collection) {
			setRangeVariables(rangeVars, index++, object);
			if (writeRangeItem(writer, rangeNode, object)) {
				return;
			}
		}
	}

	private void iterateMap(Writer writer, RangeNode rangeNode, List<VariableNode> rangeVars, Map<?, ?> map,
			Object data, BeanInfo beanInfo, Map<String, Object> savedVars)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		if (map.isEmpty() && rangeNode.getElseListNode() != null) {
			writeNode(writer, rangeNode.getElseListNode(), data, beanInfo);
			restoreVariables(rangeVars, savedVars);
			return;
		}
		// Go text/template guarantees sorted-key iteration for maps with
		// comparable keys
		List<Map.Entry<?, ?>> sorted = new ArrayList<>(map.entrySet());
		sorted.sort(Comparator.comparing((e) -> String.valueOf(e.getKey())));
		for (Map.Entry<?, ?> entry : sorted) {
			setRangeVariables(rangeVars, entry.getKey(), entry.getValue());
			if (writeRangeItem(writer, rangeNode, entry.getValue())) {
				return;
			}
		}
	}

	private void setRangeVariables(List<VariableNode> rangeVars, Object keyOrIndex, Object value) {
		if (rangeVars.size() == 1) {
			// Single variable gets the value
			variables.put(rangeVars.get(0).getIdentifier(0), value);
		}
		else if (rangeVars.size() >= 2) {
			// Two variables: first is key/index, second is value
			variables.put(rangeVars.get(0).getIdentifier(0), keyOrIndex);
			variables.put(rangeVars.get(1).getIdentifier(0), value);
		}
	}

	private void restoreVariables(List<VariableNode> rangeVars, Map<String, Object> savedVars) {
		for (VariableNode varNode : rangeVars) {
			String varName = varNode.getIdentifier(0);
			if (savedVars.containsKey(varName)) {
				variables.put(varName, savedVars.get(varName));
			}
			else {
				variables.remove(varName);
			}
		}
	}

	/**
	 * Writes a single range iteration, handling break/continue signals.
	 * @return {@code true} if a break was signalled and the loop should exit
	 */
	private boolean writeRangeItem(Writer writer, RangeNode rangeNode, Object value)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		try {
			writeRangeValue(writer, rangeNode, value);
		}
		catch (ContinueSignal ex) {
			return false;
		}
		catch (BreakSignal ex) {
			return true;
		}
		return false;
	}

	private void writeRangeValue(Writer writer, RangeNode rangeNode, Object value)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		ListNode ifListNode = rangeNode.getIfListNode();
		BeanInfo itemBeanInfo = getBeanInfo(value);
		for (Node node : ifListNode) {
			writeNode(writer, node, value, itemBeanInfo);
		}
	}

	private void writeText(Writer writer, TextNode textNode) throws IOException {
		printText(writer, textNode.getText());
	}

	private void writeWith(Writer writer, WithNode withNode, Object data, BeanInfo beanInfo)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		Object value = executePipe(withNode.getPipeNode(), data, beanInfo);
		if (isTrue(value)) {
			BeanInfo valueBeanInfo = getBeanInfo(value);
			writeNode(writer, withNode.getIfListNode(), value, valueBeanInfo);
		}
		else if (withNode.getElseListNode() != null) {
			writeNode(writer, withNode.getElseListNode(), data, beanInfo);
		}
	}

	private void writeTemplate(Writer writer, TemplateNode templateNode, Object data, BeanInfo beanInfo)
			throws IOException, TemplateExecutionException, TemplateNotFoundException {
		String name = templateNode.getName();

		ListNode listNode = (ListNode) rootNodes.get(name);
		if (listNode == null) {
			throw new TemplateExecutionException(String.format("template %s not defined", name));
		}

		Object value = executePipe(templateNode.getPipeNode(), data, beanInfo);
		BeanInfo valueBeanInfo = (value != null) ? getBeanInfo(value) : null;
		writeNode(writer, listNode, value, valueBeanInfo);
	}

	private Object executePipe(PipeNode pipeNode, Object data, BeanInfo beanInfo) throws TemplateExecutionException {
		return executePipe(pipeNode, data, beanInfo, true);
	}

	private Object executePipeWithoutVariableAssignment(PipeNode pipeNode, Object data, BeanInfo beanInfo)
			throws TemplateExecutionException {
		return executePipe(pipeNode, data, beanInfo, false);
	}

	private Object executePipe(PipeNode pipeNode, Object data, BeanInfo beanInfo, boolean assignVariables)
			throws TemplateExecutionException {
		if (pipeNode == null) {
			return data;
		}

		Object value = null;
		for (CommandNode command : pipeNode.getCommands()) {
			value = executeCommand(command, data, beanInfo, value);
		}

		// Store variables in execution context (e.g., {{$x := .Value}})
		if (assignVariables) {
			for (VariableNode variable : pipeNode.getVariables()) {
				if (variable != null) {
					String varName = variable.getIdentifier(0);
					// Store the value even if it's null or empty string, matching Helm
					// behavior
					variables.put(varName, value);
				}
			}
		}

		return value;
	}

	private Object executeCommand(CommandNode command, Object data, BeanInfo beanInfo, Object currentPipelineValue)
			throws TemplateExecutionException {
		if (command.getArgumentCount() == 0) {
			throw new TemplateExecutionException("empty command");
		}
		Node firstArgument = command.getFirstArgument();
		if (firstArgument instanceof FieldNode fieldNode) {
			// Go templates: .obj.Method arg → call Method(arg) on obj
			if (command.getArgumentCount() > 1) {
				Object result = tryFieldMethodCall(fieldNode, command.getArguments(), data, beanInfo);
				if (result != INVOKE_NOT_FOUND) {
					return result;
				}
			}
			return executeField(fieldNode, data);
		}
		if (firstArgument instanceof IdentifierNode) {
			return executeFunction((IdentifierNode) firstArgument, command.getArguments(), data, beanInfo,
					currentPipelineValue);
		}
		if (firstArgument instanceof ChainNode chainNode) {
			// Go templates: (.pipe).Method arg or .X.Y.Method arg → method call
			if (command.getArgumentCount() > 1) {
				Object result = tryChainMethodCall(chainNode, command.getArguments(), data, beanInfo);
				if (result != INVOKE_NOT_FOUND) {
					return result;
				}
			}
			return executeChain(chainNode, data, beanInfo);
		}
		if (firstArgument instanceof VariableNode) {
			return executeVariable((VariableNode) firstArgument);
		}

		if (firstArgument instanceof DotNode) {
			return data;
		}
		if (firstArgument instanceof StringNode) {
			return ((StringNode) firstArgument).getText();
		}
		if (firstArgument instanceof BoolNode) {
			return ((BoolNode) firstArgument).isValue();
		}
		if (firstArgument instanceof NumberNode) {
			NumberNode numberNode = (NumberNode) firstArgument;
			if (numberNode.isInt()) {
				return numberNode.getIntValue();
			}
			if (numberNode.isFloat()) {
				return numberNode.getFloatValue();
			}
			return 0;
		}
		if (firstArgument instanceof NilNode) {
			return null;
		}

		if (firstArgument instanceof PipeNode) {
			return executePipe((PipeNode) firstArgument, data, beanInfo);
		}

		throw new TemplateExecutionException(String.format("can't evaluate command %s", firstArgument));
	}

	private Object executeChain(ChainNode chainNode, Object data, BeanInfo beanInfo) throws TemplateExecutionException {
		Object current = executeArgument(chainNode.getNode(), data, beanInfo);
		for (String field : chainNode.getFields()) {
			current = getFieldValue(current, field);
		}
		return current;
	}

	private Object getFieldValue(Object current, String identifier) throws TemplateExecutionException {
		if (current == null) {
			return null;
		}

		if (current instanceof Map<?, ?> rawMap) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) rawMap;
			return map.get(identifier);
		}

		BeanInfo currentBeanInfo = getBeanInfo(current);
		PropertyDescriptor[] propertyDescriptors = currentBeanInfo.getPropertyDescriptors();
		for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
			String propertyDescriptorName = propertyDescriptor.getName();
			if ("class".equals(propertyDescriptorName)) {
				continue;
			}

			String goStyleName = toGoStylePropertyName(propertyDescriptorName);
			if (identifier.equals(propertyDescriptorName) || identifier.equals(goStyleName)) {
				Method readMethod = propertyDescriptor.getReadMethod();
				try {
					return readMethod.invoke(current);
				}
				catch (IllegalAccessException | InvocationTargetException ex) {
					throw new TemplateExecutionException(String.format("can't get value '%s' from data", identifier),
							ex);
				}
			}
		}

		// In Helm, missing fields return nil instead of throwing an error
		return null;
	}

	private Object executeField(FieldNode fieldNode, Object data) throws TemplateExecutionException {
		String[] identifiers = fieldNode.getIdentifiers();
		Object current = data;
		for (String identifier : identifiers) {
			current = getFieldValue(current, identifier);
		}

		return current;
	}

	// Sentinel value indicating that no matching method was found
	private static final Object INVOKE_NOT_FOUND = new Object();

	/**
	 * Try to invoke the last identifier in a field chain as a method call. Go templates
	 * support method calls on values: {@code .Obj.Method arg1 arg2} calls
	 * {@code Method(arg1, arg2)} on the result of {@code .Obj}.
	 * @return the method result, or {@link #INVOKE_NOT_FOUND} if no method matched
	 */
	private Object tryFieldMethodCall(FieldNode fieldNode, List<Node> cmdArgNodes, Object data, BeanInfo beanInfo)
			throws TemplateExecutionException {
		String[] identifiers = fieldNode.getIdentifiers();
		if (identifiers.length == 0) {
			return INVOKE_NOT_FOUND;
		}

		// Resolve all identifiers except the last to get the receiver object
		Object receiver = data;
		for (int i = 0; i < identifiers.length - 1; i++) {
			receiver = getFieldValue(receiver, identifiers[i]);
		}

		String methodName = identifiers[identifiers.length - 1];
		return tryMethodInvoke(receiver, methodName, cmdArgNodes, data, beanInfo);
	}

	/**
	 * Try to invoke the last field in a chain node as a method call. Handles the parser's
	 * ChainNode structure: {@code .X.Y.Method arg} where the parser creates
	 * {@code ChainNode(FieldNode("X"), ["Y", "Method"])}.
	 * @return the method result, or {@link #INVOKE_NOT_FOUND} if no method matched
	 */
	private Object tryChainMethodCall(ChainNode chainNode, List<Node> cmdArgNodes, Object data, BeanInfo beanInfo)
			throws TemplateExecutionException {
		List<String> fields = chainNode.getFields();
		if (fields.isEmpty()) {
			return INVOKE_NOT_FOUND;
		}

		// Resolve the chain's base node and all fields except the last
		Object receiver = executeArgument(chainNode.getNode(), data, beanInfo);
		for (int i = 0; i < fields.size() - 1; i++) {
			receiver = getFieldValue(receiver, fields.get(i));
		}

		String methodName = fields.get(fields.size() - 1);
		return tryMethodInvoke(receiver, methodName, cmdArgNodes, data, beanInfo);
	}

	/**
	 * Core method invocation logic shared by field and chain method calls.
	 */
	private Object tryMethodInvoke(Object receiver, String methodName, List<Node> cmdArgNodes, Object data,
			BeanInfo beanInfo) throws TemplateExecutionException {
		if (receiver == null) {
			return INVOKE_NOT_FOUND;
		}

		// Evaluate the method arguments (skip the first arg which is the node itself)
		List<Node> argNodes = cmdArgNodes.subList(1, cmdArgNodes.size());
		Object[] args = new Object[argNodes.size()];
		executeArguments(data, beanInfo, argNodes, args);

		// Try to find a matching method via reflection
		for (Method m : receiver.getClass().getMethods()) {
			if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
				try {
					return m.invoke(receiver, args);
				}
				catch (IllegalAccessException | InvocationTargetException ex) {
					if (log.isDebugEnabled()) {
						log.debug("Method invocation failed: {}.{}(): {}", receiver.getClass().getSimpleName(),
								methodName, ex.getMessage());
					}
				}
			}
		}

		return INVOKE_NOT_FOUND;
	}

	private Object executeFunction(IdentifierNode identifierNode, List<Node> cmdArgNodes, Object data,
			BeanInfo beanInfo, Object finalValue) throws TemplateExecutionException {
		String identifier = identifierNode.getIdentifier();

		if (functions.containsKey(identifier)) {
			Function function = functions.get(identifier);
			if (function == null) {
				throw new TemplateExecutionException("call of null for " + identifier);
			}

			// Fix potential IndexOutOfBoundsException by checking size before subList
			List<Node> functionArgNodes = (cmdArgNodes.size() > 1) ? cmdArgNodes.subList(1, cmdArgNodes.size())
					: Collections.emptyList();

			Object[] functionArgs;
			if (finalValue != null) {

				// per https://pkg.go.dev/text/template, "In a chained pipeline, the
				// result of
				// each command is passed as the last argument of the following command."
				// (This is necessary
				// when implementing functions like 'default', for example.)

				functionArgs = new Object[functionArgNodes.size() + 1];
				executeArguments(data, beanInfo, functionArgNodes, functionArgs);
				functionArgs[functionArgNodes.size()] = finalValue;
			}
			else {
				functionArgs = new Object[functionArgNodes.size()];
				executeArguments(data, beanInfo, functionArgNodes, functionArgs);
			}

			return function.invoke(functionArgs);
		}

		throw new TemplateExecutionException(String.format("%s is not a defined function", identifier));
	}

	private void executeArguments(Object data, BeanInfo beanInfo, List<Node> args, Object[] argumentValues)
			throws TemplateExecutionException {
		for (int i = 0; i < args.size(); i++) {
			Object value = executeArgument(args.get(i), data, beanInfo);
			argumentValues[i] = value;
		}
	}

	private Object executeArgument(Node argument, Object data, BeanInfo beanInfo) throws TemplateExecutionException {
		if (argument instanceof DotNode) {
			return data;
		}

		if (argument instanceof NilNode) {
			return null;
		}

		if (argument instanceof BoolNode) {
			BoolNode boolNode = (BoolNode) argument;
			return boolNode.isValue();
		}

		if (argument instanceof StringNode) {
			StringNode stringNode = (StringNode) argument;
			return stringNode.getText();
		}

		if (argument instanceof NumberNode) {
			NumberNode numberNode = (NumberNode) argument;
			if (numberNode.isInt()) {
				return numberNode.getIntValue();
			}
			if (numberNode.isFloat()) {
				return numberNode.getFloatValue();
			}
			return 0;
		}

		if (argument instanceof FieldNode) {
			FieldNode fieldNode = (FieldNode) argument;
			return executeField(fieldNode, data);
		}

		if (argument instanceof ChainNode) {
			ChainNode chainNode = (ChainNode) argument;
			return executeChain(chainNode, data, beanInfo);
		}

		if (argument instanceof PipeNode) {
			PipeNode pipeNode = (PipeNode) argument;
			return executePipe(pipeNode, data, beanInfo);
		}

		if (argument instanceof VariableNode) {
			return executeVariable((VariableNode) argument);
		}

		if (argument instanceof CommandNode) {
			CommandNode commandNode = (CommandNode) argument;
			return executeCommand(commandNode, data, beanInfo, null);
		}

		if (argument instanceof IdentifierNode) {
			IdentifierNode identifierNode = (IdentifierNode) argument;
			String identifier = identifierNode.getIdentifier();
			// Check if it's a function
			if (functions.containsKey(identifier)) {
				Function function = functions.get(identifier);
				if (function != null) {
					return function.invoke(new Object[0]);
				}
			}
			// Otherwise just return the identifier as a string
			return identifier;
		}

		throw new TemplateExecutionException(String.format("can't extract value of argument %s (type: %s)", argument,
				argument.getClass().getSimpleName()));
	}

	private Object executeVariable(VariableNode variableNode) throws TemplateExecutionException {
		String varName = variableNode.getIdentifier(0);
		if (!variables.containsKey(varName)) {
			throw new TemplateExecutionException(String.format("undefined variable: %s", varName));
		}
		return variables.get(varName);
	}

	/**
	 * Introspect the data object with caching for performance
	 * @param data Data object for the template
	 * @return BeanInfo telling the details of data object
	 */
	private BeanInfo getBeanInfo(Object data) {
		if (data == null) {
			return null;
		}
		if (data instanceof Map) {
			return null; // We handle Maps directly in field access
		}

		Class<?> type = data.getClass();

		return beanInfoCache.computeIfAbsent(type, (clazz) -> {
			try {
				return Introspector.getBeanInfo(clazz);
			}
			catch (IntrospectionException ex) {
				if (log.isDebugEnabled()) {
					log.debug("Failed to introspect class {}: {}", clazz.getName(), ex.getMessage());
				}
				return null;
			}
		});
	}

	/**
	 * Get go style property name
	 * @param propertyDescriptorName Name of property in an object
	 * @return Go style property name
	 */
	private String toGoStylePropertyName(String propertyDescriptorName) {
		return Character.toUpperCase(propertyDescriptorName.charAt(0)) + propertyDescriptorName.substring(1);
	}

	/**
	 * Determine if a pipe evaluation returns a positive result, such as 'true' for a
	 * bool, a none-null value for an object, a none-empty array or list
	 * @param value The result of the pipe evaluation
	 * @return true if evaluation returns a positive result
	 */
	private boolean isTrue(Object value) {
		return Functions.isTrue(value);
	}

	private void printText(Writer writer, String text) throws IOException {
		writer.write(text);
	}

	private void printValue(Writer writer, Object value) throws IOException {
		ValuePrinter.printValue(writer, value);
	}

}
