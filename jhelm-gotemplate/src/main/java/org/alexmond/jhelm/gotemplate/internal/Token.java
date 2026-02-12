package org.alexmond.jhelm.gotemplate.internal;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@RequiredArgsConstructor
@Accessors(fluent = true)
public class Token {

    private final TokenType type;
    private final String val;
    private final int pos;
    private final int line;
    private final int column;

    public String value() {
        return val;
    }

    @Override
    public String toString() {
        switch (type) {
            case EOF:
                return "EOF";
            case KEYWORD:
                return '<' + val + '>';
            case ERROR:
            default:
                return val;
        }
    }
}
