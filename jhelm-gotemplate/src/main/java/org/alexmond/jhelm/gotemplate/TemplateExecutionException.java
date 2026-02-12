package org.alexmond.jhelm.gotemplate;

public class TemplateExecutionException extends TemplateException {

    public TemplateExecutionException(String message) {
        super(message);
    }

    public TemplateExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
