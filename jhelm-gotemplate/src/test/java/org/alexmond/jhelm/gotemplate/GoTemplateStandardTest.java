package org.alexmond.jhelm.gotemplate;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * These test cases are the same as those in go standard library
 */
class GoTemplateStandardTest {

    @Test
    void test() throws IOException, TemplateParseException, TemplateNotFoundException, TemplateExecutionException {
        String letter = """

                Dear {{.Name}},
                {{if .Attended}}
                It was a pleasure to see you at the wedding.
                {{- else}}
                It is a shame you couldn't make it to the wedding.
                {{- end}}
                {{with .Gift -}}
                Thank you for the lovely {{.}}.
                {{end}}
                Best wishes,
                Josie
                """;


        GoTemplateFactory goTemplateFactory = new GoTemplateFactory();
        goTemplateFactory.parse("letter", letter);


        GoTemplate goTemplate = goTemplateFactory.getTemplate("letter");


        Writer writer = new StringWriter();
        goTemplate.execute(new Recipient("Aunt Mildred", "bone china tea set", true), writer);

        assertEquals(
                """

                        Dear Aunt Mildred,

                        It was a pleasure to see you at the wedding.
                        Thank you for the lovely bone china tea set.

                        Best wishes,
                        Josie
                        """,
                writer.toString()
        );


        Writer writer2 = new StringWriter();
        goTemplate.execute(new Recipient("Uncle John", "moleskin pants", false), writer2);

        assertEquals(
                """

                        Dear Uncle John,

                        It is a shame you couldn't make it to the wedding.
                        Thank you for the lovely moleskin pants.

                        Best wishes,
                        Josie
                        """,
                writer2.toString());


        Writer writer3 = new StringWriter();
        goTemplate.execute(new Recipient("Cousin Rodney", "", false), writer3);

        assertEquals(
                """

                        Dear Cousin Rodney,

                        It is a shame you couldn't make it to the wedding.

                        Best wishes,
                        Josie
                        """,
                writer3.toString());
    }


    @Test
    void testDefinition() throws IOException, TemplateParseException, TemplateNotFoundException, TemplateExecutionException {
        String masterTemplate = "Names:{{block \"list\" .}}{{\"\\n\"}}{{range .}}{{println \"-\" .}}{{end}}{{end}}";
        String overlayTemplate = "{{define \"list\"}} {{join . \", \"}}{{end}} ";

        String[] guardians = {"Gamora", "Groot", "Nebula", "Rocket", "Star-Lord"};

        Map<String, Function> functions = new LinkedHashMap<>();
        functions.put("join", args -> {
            CharSequence delimiter = (CharSequence) args[1];
            CharSequence[] elements = (CharSequence[]) args[0];
            return String.join(delimiter, elements);
        });


        GoTemplateFactory goTemplateFactory = new GoTemplateFactory(functions);
        goTemplateFactory.parse("master", masterTemplate);

        GoTemplate goTemplate = goTemplateFactory.getTemplate("master");

        Writer writer = new StringWriter();
        goTemplate.execute(guardians, writer);
        String text = writer.toString();

        assertEquals(
                """
                        Names:
                        - Gamora
                        - Groot
                        - Nebula
                        - Rocket
                        - Star-Lord
                        """,
                text
        );


        goTemplateFactory.parse(overlayTemplate);
        goTemplate = goTemplateFactory.getTemplate("master");

        writer = new StringWriter();
        goTemplate.execute(guardians, writer);
        String overlayText = writer.toString();

        assertEquals("Names: Gamora, Groot, Nebula, Rocket, Star-Lord", overlayText);
    }

    @Test
    void invoke() throws TemplateParseException, TemplateNotFoundException, IOException, TemplateExecutionException {
        GoTemplateFactory goTemplateFactory = new GoTemplateFactory();
        goTemplateFactory.parse("T0.tmpl", "T0 invokes T1: ({{template \"T1\"}})");
        goTemplateFactory.parse("T1.tmpl", "{{define \"T1\"}}T1 invokes T2: ({{template \"T2\"}}){{end}}");
        goTemplateFactory.parse("T2.tmpl", "{{define \"T2\"}}This is T2{{end}}");


        GoTemplate goTemplate = goTemplateFactory.getTemplate("T0.tmpl");

        StringWriter writer = new StringWriter();
        goTemplate.execute(null, writer);
        assertEquals("T0 invokes T1: (T1 invokes T2: (This is T2))", writer.toString());
    }

    @Test
    void testPrintFunction() throws IOException, TemplateParseException, TemplateNotFoundException, TemplateExecutionException {
        GoTemplateFactory factory = new GoTemplateFactory();
        factory.parse("test", "{{ print \"hello\" \"-\" \"world\" }}");

        GoTemplate template = factory.getTemplate("test");
        StringWriter writer = new StringWriter();
        template.execute(null, writer);

        // print should concatenate without spaces
        assertEquals("hello-world", writer.toString());
    }
}