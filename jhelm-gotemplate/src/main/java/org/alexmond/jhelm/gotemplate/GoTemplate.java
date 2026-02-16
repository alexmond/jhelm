package org.alexmond.jhelm.gotemplate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.internal.exec.Executor;
import org.alexmond.jhelm.gotemplate.internal.parse.Node;
import org.alexmond.jhelm.gotemplate.internal.parse.Parser;
import org.alexmond.jhelm.gotemplate.internal.util.IOUtils;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GoTemplate represents a parsed Go template.
 * It can contain multiple named templates (definitions).
 */
@Slf4j
@Getter
public class GoTemplate {

    private final Map<String, Function> functions;
    private final Map<String, Node> rootNodes;
    private String name;

    /**
     * Create a new GoTemplate with default settings
     */
    public GoTemplate() {
        this(null);
    }

    /**
     * Create a new GoTemplate with custom functions
     *
     * @param functions custom functions to add
     */
    public GoTemplate(Map<String, Function> functions) {
        LinkedHashMap<String, Function> map = new LinkedHashMap<>(Functions.BUILTIN);
        if (functions != null) {
            map.putAll(functions);
        }
        this.functions = map;
        this.functions.putAll(org.alexmond.jhelm.gotemplate.helm.HelmFunctions.getFunctions(this));
        this.rootNodes = new LinkedHashMap<>();
    }

    /**
     * Parse an unnamed template.
     *
     * @param text Template text
     */
    public GoTemplate parse(String text) throws TemplateParseException {
        return parse("", text);
    }

    /**
     * Parse a named template.
     *
     * @param name The template name
     * @param text Template text
     */
    public GoTemplate parse(String name, String text) throws TemplateParseException {
        if (this.name == null || this.name.isEmpty()) {
            this.name = name;
        }
        Parser parser = new Parser(functions);
        try {
            Map<String, Node> parsedNodes = parser.parse(name, text);
            rootNodes.putAll(parsedNodes);
        } catch (Exception e) {
            log.warn("Internal error during parsing of {}: {}. Text: {}", name, e.getMessage(), text.length() > 100 ? text.substring(0, 100) + "..." : text);
            throw new TemplateParseException("Internal error during parsing of " + name, e);
        }
        return this;
    }

    /**
     * Parse a named template from InputStream
     */
    public GoTemplate parse(String name, InputStream in) throws TemplateParseException, IOException {
        return parse(name, new InputStreamReader(in));
    }

    /**
     * Parse a named template from Reader
     */
    public GoTemplate parse(String name, Reader reader) throws TemplateParseException, IOException {
        String text = IOUtils.read(reader);
        return parse(name, text);
    }

    /**
     * Execute the main template
     */
    public void execute(Object data, Writer writer) throws IOException,
            TemplateNotFoundException, TemplateExecutionException {
        execute(name, data, writer);
    }

    /**
     * Execute a named template from this template set
     */
    public void execute(String name, Object data, Writer writer) throws IOException,
            TemplateNotFoundException, TemplateExecutionException {
        if (name == null || !rootNodes.containsKey(name)) {
            throw new TemplateNotFoundException(String.format("Template '%s' not found.", name));
        }
        Executor executor = new Executor(rootNodes, functions);
        executor.execute(name, data, writer);
    }

    /**
     * Check if a named template exists.
     *
     * @param name the template name to check
     * @return {@code true} if a template with the given name exists, {@code false} otherwise
     */
    public boolean hasTemplate(String name) {
        return rootNodes.containsKey(name);
    }

    /**
     * Get the root node of the main template
     */
    public Node root() {
        return rootNodes.get(name);
    }

    /**
     * Get the root node of a named template
     */
    public Node root(String name) {
        return rootNodes.get(name);
    }
}
