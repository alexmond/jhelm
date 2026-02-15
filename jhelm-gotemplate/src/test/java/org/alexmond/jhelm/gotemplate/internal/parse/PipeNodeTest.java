package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipeNodeTest {

    @Test
    void testConstructorWithContext() {
        PipeNode pipe = new PipeNode("test-context");
        assertEquals("test-context", pipe.getContext());
    }

    @Test
    void testAppendVariable() {
        PipeNode pipe = new PipeNode("ctx");
        VariableNode var = new VariableNode("x");
        pipe.append(var);

        assertEquals(1, pipe.getVariableCount());
        assertEquals(1, pipe.getVariables().size());
        assertEquals(var, pipe.getVariables().get(0));
    }

    @Test
    void testAppendMultipleVariables() {
        PipeNode pipe = new PipeNode("ctx");
        VariableNode var1 = new VariableNode("x");
        VariableNode var2 = new VariableNode("y");

        pipe.append(var1);
        pipe.append(var2);

        assertEquals(2, pipe.getVariableCount());
    }

    @Test
    void testAppendCommand() {
        PipeNode pipe = new PipeNode("ctx");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("print"));

        pipe.append(cmd);

        assertEquals(1, pipe.getCommands().size());
        assertEquals(cmd, pipe.getCommands().get(0));
    }

    @Test
    void testAppendMultipleCommands() {
        PipeNode pipe = new PipeNode("ctx");
        CommandNode cmd1 = new CommandNode();
        cmd1.append(new IdentifierNode("upper"));
        CommandNode cmd2 = new CommandNode();
        cmd2.append(new IdentifierNode("trim"));

        pipe.append(cmd1);
        pipe.append(cmd2);

        assertEquals(2, pipe.getCommands().size());
    }

    @Test
    void testToStringWithCommandsOnly() {
        PipeNode pipe = new PipeNode("ctx");
        CommandNode cmd1 = new CommandNode();
        cmd1.append(new IdentifierNode("upper"));
        CommandNode cmd2 = new CommandNode();
        cmd2.append(new IdentifierNode("trim"));

        pipe.append(cmd1);
        pipe.append(cmd2);

        String result = pipe.toString();
        assertTrue(result.contains("upper"));
        assertTrue(result.contains("trim"));
        assertTrue(result.contains("|"));
    }

    @Test
    void testToStringWithVariablesAndCommands() {
        PipeNode pipe = new PipeNode("ctx");
        VariableNode var = new VariableNode("result");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("process"));

        pipe.append(var);
        pipe.append(cmd);

        String result = pipe.toString();
        assertTrue(result.contains("result"));
        assertTrue(result.contains(":="));
        assertTrue(result.contains("process"));
    }

    @Test
    void testToStringWithMultipleVariables() {
        PipeNode pipe = new PipeNode("ctx");
        VariableNode var1 = new VariableNode("x");
        VariableNode var2 = new VariableNode("y");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("split"));

        pipe.append(var1);
        pipe.append(var2);
        pipe.append(cmd);

        String result = pipe.toString();
        assertTrue(result.contains("x"));
        assertTrue(result.contains("y"));
        assertTrue(result.contains(","));
        assertTrue(result.contains(":="));
    }
}
