package org.alexmond.jhelm.gotemplate;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void test() throws IOException, TemplateException {
        Template template = new Template("demo");
        template.parse("{{ .Name }}");

        StringWriter writer = new StringWriter();

        User user = new User();
        user.setName("Bob");

        template.execute(writer, user);
        assertEquals("Bob", writer.toString());
    }

    @Test
    void testPipe() throws IOException, TemplateException {
        Template template = new Template("demo");
        template.parse("{{ .Name | print | print }}");

        StringWriter writer = new StringWriter();

        User user = new User();
        user.setName("Bob");

        template.execute(writer, user);
        assertEquals("Bob", writer.toString());
    }

    @Test
    void testOfficial() throws IOException, TemplateException {
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


        Template template = new Template("letter");
        template.parse(letter);

        Writer writer = new StringWriter();
        template.execute(writer, new Recipient("Aunt Mildred", "bone china tea set", true));

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
        template.execute(writer2, new Recipient("Uncle John", "moleskin pants", false));

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
        template.execute(writer3, new Recipient("Cousin Rodney", "", false));

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
    void testDefinition() throws IOException, TemplateException {
        String masterTemplate = "Names:{{block \"list\" .}}{{\"\\n\"}}{{range .}}{{println \"-\" .}}{{end}}{{end}}";
        String overlayTemplate = "{{define \"list\"}} {{join . \", \"}}{{end}} ";

        String[] guardians = {"Gamora", "Groot", "Nebula", "Rocket", "Star-Lord"};

        Map<String, Function> functions = new LinkedHashMap<>();
        functions.put("join", args -> {
            CharSequence delimiter = (CharSequence) args[1];
            CharSequence[] elements = (CharSequence[]) args[0];
            return String.join(delimiter, elements);
        });


        Template template = new Template("master", functions);
        template.parse(masterTemplate);

        Writer writer = new StringWriter();
        template.execute(writer, guardians);
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


        template.parse(overlayTemplate);

        writer = new StringWriter();
        template.execute(writer, guardians);
        String overlayText = writer.toString();

        assertEquals("Names: Gamora, Groot, Nebula, Rocket, Star-Lord", overlayText);
    }

    @Getter
    @Setter
    public static class User {
        private String name;
    }

}