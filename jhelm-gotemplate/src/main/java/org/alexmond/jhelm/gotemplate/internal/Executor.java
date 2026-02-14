package org.alexmond.jhelm.gotemplate.internal;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.Functions;
import org.alexmond.jhelm.gotemplate.TemplateExecutionException;
import org.alexmond.jhelm.gotemplate.TemplateNotFoundException;
import org.alexmond.jhelm.gotemplate.internal.ast.*;
import org.alexmond.jhelm.gotemplate.internal.lang.StringEscapeUtils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    public void execute(String name, Object data, Writer writer) throws IOException,
            TemplateNotFoundException, TemplateExecutionException {
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
            } catch (IndexOutOfBoundsException e) {
                log.error("Internal IndexOutOfBounds in '{}': {}", name, e.getMessage(), e);
                throw new TemplateExecutionException("Internal IndexOutOfBounds in '" + name + "': " + e.getMessage(), e);
            } catch (Exception e) {
                if (e instanceof IOException || e instanceof TemplateExecutionException || e instanceof TemplateNotFoundException) {
                    throw e;
                }
                log.error("Execution failure in template '{}': {}", name, e.getMessage(), e);
                throw new TemplateExecutionException("Execution error in '" + name + "': " + e.getMessage(), e);
            }
        } else {
            throw new TemplateExecutionException(String.format("root node for '%s' is not a ListNode", name));
        }
    }

    private void writeNode(Writer writer, Node node, Object data, BeanInfo beanInfo) throws IOException,
            TemplateExecutionException, TemplateNotFoundException {
        if (node == null) {
            return;
        }
        if (node instanceof ListNode) {
            writeList(writer, (ListNode) node, data, beanInfo);
        } else if (node instanceof ActionNode actionNode) {
            writeAction(writer, actionNode, data, beanInfo);
        } else if (node instanceof CommentNode) {
            // Ignore comment
        } else if (node instanceof IfNode ifNode) {
            writeIf(writer, ifNode, data, beanInfo);
        } else if (node instanceof RangeNode rangeNode) {
            writeRange(writer, rangeNode, data, beanInfo);
        } else if (node instanceof TemplateNode templateNode) {
            writeTemplate(writer, templateNode, data, beanInfo);
        } else if (node instanceof TextNode textNode) {
            writeText(writer, textNode);
        } else if (node instanceof WithNode withNode) {
            writeWith(writer, withNode, data, beanInfo);
        } else {
            throw new TemplateExecutionException(String.format("unknown node: %s", node.toString()));
        }
    }

    private void writeAction(Writer writer, ActionNode actionNode, Object data, BeanInfo beanInfo) throws IOException,
            TemplateExecutionException {
        PipeNode pipeNode = actionNode.getPipeNode();
        Object value = executePipe(pipeNode, data, beanInfo);

        // Debug: log variable assignments
        if (pipeNode.getVariableCount() > 0) {
            for (VariableNode variable : pipeNode.getVariables()) {
                String varName = variable.getIdentifier(0);
                log.debug("Action assigned variable: {} = {}", varName, value);
            }
        }

        if (pipeNode.getVariableCount() == 0) {
            printValue(writer, value);
        }
    }

    private void writeIf(Writer writer, IfNode ifNode, Object data, BeanInfo beanInfo) throws IOException,
            TemplateExecutionException, TemplateNotFoundException {
        Object value = executePipe(ifNode.getPipeNode(), data, beanInfo);
        if (isTrue(value)) {
            writeNode(writer, ifNode.getIfListNode(), data, beanInfo);
        } else if (ifNode.getElseListNode() != null) {
            writeNode(writer, ifNode.getElseListNode(), data, beanInfo);
        }
    }

    private void writeList(Writer writer, ListNode listNode, Object data, BeanInfo beanInfo) throws IOException,
            TemplateExecutionException, TemplateNotFoundException {
        for (Node node : listNode) {
            writeNode(writer, node, data, beanInfo);
        }
    }

    private void writeRange(Writer writer, RangeNode rangeNode, Object data, BeanInfo beanInfo) throws IOException,
            TemplateExecutionException, TemplateNotFoundException {
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
            // Restore variables
            restoreVariables(rangeVars, savedVars);
            return;
        }

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
                writeRangeValue(writer, rangeNode, value);
            }
        } else if (arrayOrList instanceof Collection<?> collection) {
            if (collection.isEmpty() && rangeNode.getElseListNode() != null) {
                writeNode(writer, rangeNode.getElseListNode(), data, beanInfo);
                restoreVariables(rangeVars, savedVars);
                return;
            }
            int index = 0;
            for (Object object : collection) {
                setRangeVariables(rangeVars, index++, object);
                writeRangeValue(writer, rangeNode, object);
            }
        } else if (arrayOrList instanceof Map<?, ?> map) {
            if (map.isEmpty() && rangeNode.getElseListNode() != null) {
                writeNode(writer, rangeNode.getElseListNode(), data, beanInfo);
                restoreVariables(rangeVars, savedVars);
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                setRangeVariables(rangeVars, entry.getKey(), entry.getValue());
                writeRangeValue(writer, rangeNode, entry.getValue());
            }
        } else {
            restoreVariables(rangeVars, savedVars);
            throw new TemplateExecutionException(String.format("can't iterate over %s", arrayOrList.getClass().getName()));
        }

        // Restore variables after range
        restoreVariables(rangeVars, savedVars);
    }

    private void setRangeVariables(List<VariableNode> rangeVars, Object keyOrIndex, Object value) {
        if (rangeVars.size() == 1) {
            // Single variable gets the value
            variables.put(rangeVars.get(0).getIdentifier(0), value);
        } else if (rangeVars.size() >= 2) {
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
            } else {
                variables.remove(varName);
            }
        }
    }

    private void writeRangeValue(Writer writer, RangeNode rangeNode, Object value) throws IOException,
            TemplateExecutionException, TemplateNotFoundException {
        ListNode ifListNode = rangeNode.getIfListNode();
        BeanInfo itemBeanInfo = getBeanInfo(value);
        for (Node node : ifListNode) {
            writeNode(writer, node, value, itemBeanInfo);
        }
    }

    private void writeText(Writer writer, TextNode textNode) throws IOException {
        printText(writer, textNode.getText());
    }

    private void writeWith(Writer writer, WithNode withNode, Object data, BeanInfo beanInfo) throws IOException,
            TemplateExecutionException, TemplateNotFoundException {
        Object value = executePipe(withNode.getPipeNode(), data, beanInfo);
        if (isTrue(value)) {
            BeanInfo valueBeanInfo = getBeanInfo(value);
            writeNode(writer, withNode.getIfListNode(), value, valueBeanInfo);
        } else if (withNode.getElseListNode() != null) {
            writeNode(writer, withNode.getElseListNode(), data, beanInfo);
        }
    }

    private void writeTemplate(Writer writer, TemplateNode templateNode, Object data, BeanInfo beanInfo) throws IOException,
            TemplateExecutionException, TemplateNotFoundException {
        String name = templateNode.getName();

        ListNode listNode = (ListNode) rootNodes.get(name);
        if (listNode == null) {
            throw new TemplateExecutionException(String.format("template %s not defined", name));
        }

        Object value = executePipe(templateNode.getPipeNode(), data, beanInfo);
        BeanInfo valueBeanInfo = value != null ? getBeanInfo(value) : null;
        writeNode(writer, listNode, value, valueBeanInfo);
    }

    private Object executePipe(PipeNode pipeNode, Object data, BeanInfo beanInfo) throws TemplateExecutionException {
        return executePipe(pipeNode, data, beanInfo, true);
    }

    private Object executePipeWithoutVariableAssignment(PipeNode pipeNode, Object data, BeanInfo beanInfo) throws TemplateExecutionException {
        return executePipe(pipeNode, data, beanInfo, false);
    }

    private Object executePipe(PipeNode pipeNode, Object data, BeanInfo beanInfo, boolean assignVariables) throws TemplateExecutionException {
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
                    // Store the value even if it's null or empty string, matching Helm behavior
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
        if (firstArgument instanceof FieldNode) {
            return executeField((FieldNode) firstArgument, data, beanInfo);
        }
        if (firstArgument instanceof IdentifierNode) {
            return executeFunction((IdentifierNode) firstArgument, command.getArguments(), data, beanInfo, currentPipelineValue);
        }
        if (firstArgument instanceof ChainNode) {
            return executeChain((ChainNode) firstArgument, data, beanInfo);
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
            if (numberNode.isInt()) return numberNode.getIntValue();
            if (numberNode.isFloat()) return numberNode.getFloatValue();
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

        if (current instanceof Map) {
            //noinspection unchecked
            Map<String, Object> map = (Map<String, Object>) current;
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
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new TemplateExecutionException(String.format(
                            "can't get value '%s' from data", identifier), e);
                }
            }
        }

        // In Helm, missing fields return nil instead of throwing an error
        return null;
    }

    private Object executeField(FieldNode fieldNode, Object data, BeanInfo beanInfo)
            throws TemplateExecutionException {
        String[] identifiers = fieldNode.getIdentifiers();
        Object current = data;
        for (String identifier : identifiers) {
            current = getFieldValue(current, identifier);
        }

        return current;
    }

    private Object executeFunction(IdentifierNode identifierNode, List<Node> cmdArgNodes, Object data, BeanInfo beanInfo,
                                   Object finalValue) throws TemplateExecutionException {
        String identifier = identifierNode.getIdentifier();

        if (functions.containsKey(identifier)) {
            Function function = functions.get(identifier);
            if (function == null) {
                throw new TemplateExecutionException("call of null for " + identifier);
            }

            // Fix potential IndexOutOfBoundsException by checking size before subList
            List<Node> functionArgNodes = cmdArgNodes.size() > 1 ? cmdArgNodes.subList(1, cmdArgNodes.size()) : java.util.Collections.emptyList();

            Object[] functionArgs;
            if (finalValue != null) {

                // per https://pkg.go.dev/text/template, "In a chained pipeline, the result of
                // each command is passed as the last argument of the following command." (This is necessary
                // when implementing functions like 'default', for example.)

                functionArgs = new Object[functionArgNodes.size() + 1];
                executeArguments(data, beanInfo, functionArgNodes, functionArgs);
                functionArgs[functionArgNodes.size()] = finalValue;
            } else {
                functionArgs = new Object[functionArgNodes.size()];
                executeArguments(data, beanInfo, functionArgNodes, functionArgs);
            }

            return function.invoke(functionArgs);
        }

        throw new TemplateExecutionException(String.format("%s is not a defined function", identifier));
    }

    private void executeArguments(Object data, BeanInfo beanInfo, List<Node> args, Object[] argumentValues) throws TemplateExecutionException {
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
            return executeField(fieldNode, data, beanInfo);
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

        throw new TemplateExecutionException(String.format("can't extract value of argument %s (type: %s)", argument, argument.getClass().getSimpleName()));
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
     *
     * @param data Data object for the template
     * @return BeanInfo telling the details of data object
     */
    private BeanInfo getBeanInfo(Object data) {
        if (data == null) return null;
        if (data instanceof Map) return null; // We handle Maps directly in field access

        Class<?> type = data.getClass();

        return beanInfoCache.computeIfAbsent(type, clazz -> {
            try {
                return Introspector.getBeanInfo(clazz);
            } catch (IntrospectionException e) {
                log.debug("Failed to introspect class {}: {}", clazz.getName(), e.getMessage());
                return null;
            }
        });
    }


    /**
     * Get go style property name
     *
     * @param propertyDescriptorName Name of property in an object
     * @return Go style property name
     */
    private String toGoStylePropertyName(String propertyDescriptorName) {
        return Character.toUpperCase(propertyDescriptorName.charAt(0)) + propertyDescriptorName.substring(1);
    }


    /**
     * Determine if a pipe evaluation returns a positive result, such as 'true' for a bool,
     * a none-null value for an object, a none-empty array or list
     *
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
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            String unescaped = StringEscapeUtils.unescape(s);
            writer.write(unescaped);
        } else if (value instanceof Number || value instanceof Boolean) {
            writer.write(String.valueOf(value));
        } else {
            // Avoid calling toString on complex objects which might trigger Introspector/reflection loops
            writer.write("[object " + value.getClass().getSimpleName() + "]");
        }
    }
}
