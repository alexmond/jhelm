package org.alexmond.jhelm.gotemplate.internal.parse;

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
        return switch (type) {
            case EOF -> "EOF";
            case KEYWORD -> '<' + val + '>';
            case ERROR -> val;
            default -> val;
        };
    }
}
