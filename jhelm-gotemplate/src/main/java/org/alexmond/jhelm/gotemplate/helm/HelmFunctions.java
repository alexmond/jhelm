package org.alexmond.jhelm.gotemplate.helm;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.GoTemplateFactory;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class HelmFunctions {

    public static Map<String, Function> getFunctions(GoTemplateFactory factory) {
        Map<String, Function> functions = new HashMap<>();

        functions.put("include", args -> {
            if (args.length < 2) return "";
            String name = String.valueOf(args[0]);
            Object data = args[1];
            try {
                GoTemplate template = factory.getTemplate(name);
                StringWriter writer = new StringWriter();
                template.execute(data, writer);
                return writer.toString();
            } catch (Exception e) {
                return "";
            }
        });

        functions.put("tpl", args -> {
            if (args.length < 2) return "";
            String text = String.valueOf(args[0]);
            Object data = args[1];
            try {
                GoTemplateFactory tplFactory = new GoTemplateFactory(factory.getFunctions());
                tplFactory.getRootNodes().putAll(factory.getRootNodes());
                tplFactory.parse("inline", text);
                GoTemplate template = tplFactory.getTemplate("inline");
                StringWriter writer = new StringWriter();
                template.execute(data, writer);
                return writer.toString();
            } catch (Exception e) {
                return "";
            }
        });

        functions.put("toYaml", args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                Class<?> yamlFactoryClass = Class.forName("com.fasterxml.jackson.dataformat.yaml.YAMLFactory");
                Class<?> objectMapperClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
                Object yamlFactory = yamlFactoryClass.getDeclaredConstructor().newInstance();
                Object mapper = objectMapperClass.getDeclaredConstructor(yamlFactoryClass).newInstance(yamlFactory);

                java.lang.reflect.Method disableMethod = objectMapperClass.getMethod("disable", Class.forName("com.fasterxml.jackson.databind.SerializationFeature"));
                disableMethod.invoke(mapper, Enum.valueOf((Class<Enum>) Class.forName("com.fasterxml.jackson.databind.SerializationFeature"), "WRITE_DATES_AS_TIMESTAMPS"));

                java.lang.reflect.Method writeValueAsString = objectMapperClass.getMethod("writeValueAsString", Object.class);
                String yaml = (String) writeValueAsString.invoke(mapper, args[0]);
                if (yaml.startsWith("---\n")) yaml = yaml.substring(4);
                return yaml.trim();
            } catch (Exception e) {
                return String.valueOf(args[0]);
            }
        });

        functions.put("fromYaml", args -> {
            if (args.length == 0 || args[0] == null) return Map.of();
            try {
                Class<?> yamlFactoryClass = Class.forName("com.fasterxml.jackson.dataformat.yaml.YAMLFactory");
                Class<?> objectMapperClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
                Object yamlFactory = yamlFactoryClass.getDeclaredConstructor().newInstance();
                Object mapper = objectMapperClass.getDeclaredConstructor(yamlFactoryClass).newInstance(yamlFactory);

                java.lang.reflect.Method readValue = objectMapperClass.getMethod("readValue", String.class, Class.class);
                return (Map<?, ?>) readValue.invoke(mapper, String.valueOf(args[0]), Map.class);
            } catch (Exception e) {
                return Map.of();
            }
        });

        // Helm-specific stubs for common required functions
        functions.put("lookup", args -> Map.of());
        functions.put("semverCompare", args -> true);
        functions.put("buildCustomCert", args -> {
            // Stub for TLS certificate generation used in Bitnami charts
            // Returns a Map with cert and key fields
            Map<String, String> cert = new HashMap<>();
            cert.put("Cert", "-----BEGIN CERTIFICATE-----\n...stub...\n-----END CERTIFICATE-----");
            cert.put("Key", "-----BEGIN PRIVATE KEY-----\n...stub...\n-----END PRIVATE KEY-----");
            return cert;
        });

        return functions;
    }
}
