package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandNodeTest {

    @Test
    void testAppendArgument() {
        CommandNode command = new CommandNode();
        IdentifierNode arg = new IdentifierNode("print");
        command.append(arg);

        assertEquals(1, command.getArgumentCount());
        assertEquals(arg, command.getFirstArgument());
        assertEquals(arg, command.getLastArgument());
    }

    @Test
    void testAppendMultipleArguments() {
        CommandNode command = new CommandNode();
        IdentifierNode arg1 = new IdentifierNode("upper");
        StringNode arg2 = new StringNode("\"hello\"");

        command.append(arg1);
        command.append(arg2);

        assertEquals(2, command.getArgumentCount());
        assertEquals(arg1, command.getFirstArgument());
        assertEquals(arg2, command.getLastArgument());
    }

    @Test
    void testGetArguments() {
        CommandNode command = new CommandNode();
        IdentifierNode arg1 = new IdentifierNode("add");
        NumberNode arg2 = new NumberNode("5");
        NumberNode arg3 = new NumberNode("3");

        command.append(arg1);
        command.append(arg2);
        command.append(arg3);

        assertEquals(3, command.getArgumentCount());
        assertEquals(3, command.getArguments().size());
    }

    @Test
    void testToStringWithSingleArgument() {
        CommandNode command = new CommandNode();
        command.append(new IdentifierNode("print"));

        assertEquals("print", command.toString());
    }

    @Test
    void testToStringWithMultipleArguments() {
        CommandNode command = new CommandNode();
        command.append(new IdentifierNode("add"));
        command.append(new NumberNode("5"));
        command.append(new NumberNode("3"));

        assertEquals("add 5 3", command.toString());
    }

    @Test
    void testToStringWithPipeNodeArgument() {
        CommandNode command = new CommandNode();
        command.append(new IdentifierNode("func"));
        PipeNode pipeArg = new PipeNode("test");
        command.append(pipeArg);

        String result = command.toString();
        assertTrue(result.contains("func"));
        assertTrue(result.contains("("));
        assertTrue(result.contains(")"));
    }

    @Test
    void testEmptyCommand() {
        CommandNode command = new CommandNode();
        assertEquals(0, command.getArgumentCount());
        assertEquals("", command.toString());
    }
}
