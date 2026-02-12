package org.alexmond.jhelm.gotemplate;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.internal.IOUtils;
import org.alexmond.jhelm.gotemplate.internal.Parser;
import org.alexmond.jhelm.gotemplate.internal.ast.Node;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating and managing Go templates
 */
@Slf4j
public class GoTemplateFactory {

    private final Map<String, Function> functions;

    private final Map<String, Node> rootNodes;


    /**
     * Factory constructor, using default settings
     */
    public GoTemplateFactory() {
        this(null);
    }

    /**
     * Factory constructor, you can configure the available functions
     *
     * @param functions The functions map, will merge the builtin functions
     */
    public GoTemplateFactory(Map<String, Function> functions) {
        LinkedHashMap<String, Function> map = new LinkedHashMap<>(Functions.BUILTIN);
        if (functions != null) {
            map.putAll(functions);
        }
        this.functions = map;
        this.functions.putAll(org.alexmond.jhelm.gotemplate.helm.HelmFunctions.getFunctions(this));
        this.rootNodes = new LinkedHashMap<>();
    }


    /**
     * Parse an unnamed template. Some templates contain names in their contents, like {@code define}, {@code block} etc.
     * They are treated as library templates.
     *
     * @param text Template text
     */
    public void parse(String text) throws TemplateParseException {
        parse("", text);
    }

    /**
     * Parse a named template, then you can obtain the template using
     * {@link GoTemplateFactory#getTemplate(String)} by its name
     *
     * @param name The template name
     * @param text Template text
     */
    public void parse(String name, String text) throws TemplateParseException {
        Parser parser = new Parser(functions);
        try {
            Map<String, Node> parsedNodes = parser.parse(name, text);
            rootNodes.putAll(parsedNodes);
        } catch (Exception e) {
            log.warn("Internal error during parsing of {}: {}. Text: {}", name, e.getMessage(), text.length() > 100 ? text.substring(0, 100) + "..." : text);
            throw new TemplateParseException("Internal error during parsing of " + name, e);
        }
    }

    /**
     * Parse a named template, then you can obtain the template using
     * {@link GoTemplateFactory#getTemplate(String)} by its name
     *
     * @param name The template name
     * @param in   Template text input stream
     */
    public void parse(String name, InputStream in) throws TemplateParseException, IOException {
        parse(name, new InputStreamReader(in));
    }

    /**
     * Parse a named template from a {@link Reader}, then you can obtain the template using
     * {@link GoTemplateFactory#getTemplate(String)} by its name
     *
     * @param name   The template name
     * @param reader Template text reader
     * @throws IOException if fail on reading the content
     */
    public void parse(String name, Reader reader) throws TemplateParseException, IOException {
        String text = IOUtils.read(reader);
        parse(name, text);
    }

    /**
     * Get a named template
     *
     * @param name Template name
     * @return the template with the name
     * @throws TemplateNotFoundException if template is missing. Did you put or parse it?
     */
    public GoTemplate getTemplate(String name) throws TemplateNotFoundException {
        Node rootNode = rootNodes.get(name);
        if (rootNode == null) {
            throw new TemplateNotFoundException(String.format("Template '%s' not found.", name));
        }
        return new GoTemplate(this, name, rootNode);
    }

    public Map<String, Function> getFunctions() {
        return functions;
    }

    public Map<String, Node> getRootNodes() {
        return rootNodes;
    }

}
