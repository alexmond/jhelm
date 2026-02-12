package org.alexmond.jhelm.gotemplate.internal;

import org.alexmond.jhelm.gotemplate.internal.lang.CharUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Lexer {

    private static final String DEFAULT_LEFT_DELIM = "{{";
    private static final String DEFAULT_RIGHT_DELIM = "}}";
    private static final String DEFAULT_LEFT_COMMENT = "/*";
    private static final String DEFAULT_RIGHT_COMMENT = "*/";

    private static final char TRIM_MARKER = '-';
    private static final int TRIM_MARKER_LENGTH = 1;


    private static final Map<String, TokenType> KEY_MAP = new LinkedHashMap<>();

    static {
        KEY_MAP.put(".", TokenType.DOT);
        KEY_MAP.put("block", TokenType.BLOCK);
        KEY_MAP.put("define", TokenType.DEFINE);
        KEY_MAP.put("else", TokenType.ELSE);
        KEY_MAP.put("end", TokenType.END);
        KEY_MAP.put("if", TokenType.IF);
        KEY_MAP.put("nil", TokenType.NIL);
        KEY_MAP.put("range", TokenType.RANGE);
        KEY_MAP.put("template", TokenType.TEMPLATE);
        KEY_MAP.put("with", TokenType.WITH);
    }


    private final String input;
    private final boolean keepComments;

    private final String leftDelimiter;
    private final String rightDelimiter;

    private final String leftComment;
    private final String rightComment;
    private final List<Token> tokens = new ArrayList<>(8);
    /**
     * Current position of the input
     */
    private int pos = 0;
    /**
     * Start position of current token
     */
    private int start = 0;
    private int parenDepth = 0;
    /**
     * The count of newline have met + 1
     */
    private int line = 1;
    /**
     * Start line of current token
     */
    private int startLine = line;
    /**
     * Start line of current token
     */
    private int lineStart = 0;



    /* Result tokens */
    /**
     * Start line of current token
     */
    private int startLineStart = 1;


    public Lexer(String input) {
        this(input, false);
    }

    public Lexer(String input, boolean keepComments) {
        this(input, keepComments, DEFAULT_LEFT_DELIM, DEFAULT_RIGHT_DELIM, DEFAULT_LEFT_COMMENT, DEFAULT_RIGHT_COMMENT);
    }

    public Lexer(String input, boolean keepComments, String leftDelimiter, String rightDelimiter, String leftComment, String rightComment) {
        if (input == null) {
            throw new NullPointerException();
        }

        this.input = input;
        this.keepComments = keepComments;
        this.leftDelimiter = leftDelimiter;
        this.rightDelimiter = rightDelimiter;
        this.leftComment = leftComment;
        this.rightComment = rightComment;

        parse();
    }

    /**
     * Parse and handle states
     */
    private void parse() {
        State state = parseText();
        while (state != null) {
            state = state.run();
        }
    }

    private State parseText() {
        moveStartToPos();

        int leftDelimPos = input.indexOf(leftDelimiter, pos);
        if (leftDelimPos == -1) {
            if (pos < input.length()) {
                goUntil(ch -> pos == input.length());

                addToken(TokenType.TEXT, getText(), start, startLine, start + 1);
            }

            moveStartToPos();

            addToken(TokenType.EOF, "", start, startLine, start - lineStart + 1);

            return null;
        } else {
            goUntil(ch -> pos == leftDelimPos);

            int eotPos = leftDelimPos;

            boolean posAtLeftDelimWithTrimMarker = isPosAtLeftDelimWithTrimMarker();
            if (posAtLeftDelimWithTrimMarker) {
                for (; eotPos > start && eotPos > 0; ) {
                    // Check bounds before accessing charAt
                    if (eotPos - 1 < 0) {
                        break;
                    }
                    char ch = input.charAt(eotPos - 1);
                    if (!CharUtils.isSpace(ch)) {
                        break;
                    }
                    eotPos--;
                }
            }

            if (eotPos > start) {
                String text = input.substring(start, eotPos);
                addToken(TokenType.TEXT, text, start, startLine, start + 1);
            }

            moveStartToPos();

            return this::parseLeftDelim;
        }
    }

    private State parseLeftDelim() {
        boolean atLeftTrimMarker = isPosAtLeftDelimWithTrimMarker();
        int delimLen = leftDelimiter.length();
        int delimStart = pos;

        pos += delimLen;

        if (atLeftTrimMarker) {
            pos += 1; // Skip the '-' marker
            // Also consume any immediately following spaces
            while (pos < input.length() && CharUtils.isSpace(input.charAt(pos))) {
                if (CharUtils.isNewline(input.charAt(pos))) {
                    addLine();
                }
                pos++;
            }
        }

        int n = input.indexOf(leftComment, pos);
        boolean hasComment = n >= 0 && n == pos; // Must start immediately after delim
        if (hasComment) {
            moveStartToPos();
            return this::parseComment;
        }

        // Only add LEFT_DELIM token if there's no comment
        // Add the delimiter token with just the delimiter text, not trim markers or spaces
        addToken(TokenType.LEFT_DELIM, leftDelimiter, delimStart, line, delimStart - lineStart + 1);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseComment() {
        int parseStart = pos + leftComment.length();
        int n = input.indexOf(rightComment, parseStart);
        if (n < 0) {
            return parseError("unclosed comment");
        }

        pos = n + rightComment.length();
        int commentEndPos = pos;  // Save position for COMMENT token

        // Check if we're immediately at right delimiter
        boolean posAtRightDelim = isPosAtRightDelim();
        boolean hasTrimMarker = false;
        boolean hasError = false;

        if (!posAtRightDelim) {
            // Try skipping spaces to see if delimiter is there
            while (pos < input.length() && CharUtils.isSpace(input.charAt(pos))) {
                if (CharUtils.isNewline(input.charAt(pos))) {
                    addLine();
                }
                pos++;
            }

            // Check again after skipping spaces
            posAtRightDelim = isPosAtRightDelim();
            if (posAtRightDelim) {
                hasTrimMarker = isPosAtRightDelimWithTrimMarker();
                // Spaces are only allowed if there's a trim marker
                if (!hasTrimMarker) {
                    hasError = true;
                }
            } else {
                // Not at delimiter even after skipping spaces
                hasError = true;
            }
        } else {
            hasTrimMarker = isPosAtRightDelimWithTrimMarker();
        }

        // Emit COMMENT token only if no errors
        if (!hasError && keepComments) {
            int savedPos = pos;
            pos = commentEndPos;
            addToken(TokenType.COMMENT);
            pos = savedPos;
        }

        if (hasError) {
            return parseError("comment closed leaving delim still open");
        }

        moveStartToPos();

        // We're at right delimiter (possibly with trim marker)
        if (hasTrimMarker) {
            pos += TRIM_MARKER_LENGTH + rightDelimiter.length();
            // Trim leading whitespace from next text
            int sotPos = pos;
            for (; sotPos < input.length(); sotPos++) {
                char ch = input.charAt(sotPos);
                if (!CharUtils.isSpace(ch)) {
                    break;
                }
            }
            pos = sotPos;
        } else {
            pos += rightDelimiter.length();
        }
        moveStartToPos();

        return this::parseText;
    }

    private State parseInsideAction() {
        boolean posAtRightDelim = isPosAtRightDelim();
        if (posAtRightDelim) {
            if (parenDepth != 0) {
                return parseError("unclosed left paren");
            }

            return this::parseRightDelim;
        }

        char ch = getCurrentCharAndGoToNext();
        if (ch == CharUtils.EOF) {
            return parseError("unclosed action");
        }

        if (CharUtils.isSpace(ch)) {
            return this::parseSpace;
        }

        if (ch == ':') {
            return this::parseDeclare;
        }

        if (ch == '=') {
            addToken(TokenType.ASSIGN);
            moveStartToPos();
            return this::parseInsideAction;
        }

        if (ch == '|') {
            return this::parsePipe;
        }

        if (ch == '"') {
            return this::parseQuote;
        }

        if (ch == '`') {
            return this::parseRawQuote;
        }

        if (ch == '$') {
            return this::parseVariable;
        }

        if (ch == '\'') {
            return this::parseChar;
        }

        if (ch == '.') {
            return this::parseDot;
        }

        if (ch == '+' || ch == '-') {
            char next = getCurrentChar();
            if (CharUtils.isNumeric(next) || next == '.') {
                movePosToStart();
                return this::parseNumber;
            }
            addToken(TokenType.CHAR);
            moveStartToPos();
            return this::parseInsideAction;
        }

        if (CharUtils.isNumeric(ch)) {
            movePosToStart();
            return this::parseNumber;
        }

        if (isAlphabetic(ch)) {
            movePosToStart();
            return this::parseIdentifier;
        }

        if (ch == '(') {
            parenDepth++;
            addToken(TokenType.LEFT_PAREN);
        } else if (ch == ')') {
            parenDepth--;
            if (parenDepth < 0) {
                return parseError("unexpected right paren");
            }
            addToken(TokenType.RIGHT_PAREN);
        } else if (CharUtils.isAscii(ch) && CharUtils.isVisible(ch)) {
            addToken(TokenType.CHAR);
        } else {
            return parseError("bad character in action: " + ch);
        }

        moveStartToPos();

        return this::parseInsideAction;
    }

    private boolean isAlphabetic(char ch) {
        return ch == '_' || Character.isLetter(ch);
    }

    private State parseRightDelim() {
        boolean posAtRightDelimWithTrimMarker = isPosAtRightDelimWithTrimMarker();
        if (posAtRightDelimWithTrimMarker) {
            pos += 1; // skip '-'
        }

        // Update start to be at the beginning of the right delimiter
        moveStartToPos();

        // Now advance pos to include the right delimiter
        pos += rightDelimiter.length();
        addToken(TokenType.RIGHT_DELIM);
        moveStartToPos();

        if (posAtRightDelimWithTrimMarker) {
            char current = getCurrentChar();
            if (current != CharUtils.EOF) {
                goUntil(ch -> !CharUtils.isSpace(ch));
                moveStartToPos();
            }
        }

        return this::parseText;
    }

    private State parseSpace() {
        goUntil(ch -> !CharUtils.isSpace(ch));

        // Don't emit SPACE token if we're immediately before a right trim marker
        // The trim marker will consume these spaces
        if (!isPosAtRightDelimWithTrimMarker()) {
            addToken(TokenType.SPACE);
        }
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseDeclare() {
        char ch = getCurrentCharAndGoToNext();
        if (ch != '=') {
            return () -> parseError("expected :=");
        }

        addToken(TokenType.DECLARE);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parsePipe() {
        addToken(TokenType.PIPE);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseQuote() {
        while (true) {
            char ch = getCurrentCharAndGoToNext();
            if (ch == '\\') {
                ch = getCurrentCharAndGoToNext();
                if (!CharUtils.isAnyOf(ch, CharUtils.EOF, CharUtils.NEW_LINE)) {
                    continue;
                }
            }

            if (CharUtils.isAnyOf(ch, CharUtils.EOF, CharUtils.NEW_LINE)) {
                return parseError("unterminated quoted string");
            }

            if (ch == '"') {
                break;
            }
        }

        addToken(TokenType.STRING);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseRawQuote() {
        while (true) {
            char ch = getCurrentCharAndGoToNext();
            if (ch == CharUtils.EOF) {
                return parseError("unclosed raw quote");
            }

            if (ch == '`') {
                break;
            }
        }

        addToken(TokenType.RAW_STRING);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseVariable() {
        if (isPosAtWordTerminator()) {
            addToken(TokenType.VARIABLE);
            moveStartToPos();

            return this::parseInsideAction;
        }

        char ch = goUntil(c -> !CharUtils.isAlphabetic(c));
        if (!isPosAtWordTerminator()) {
            return () -> parseError("bad character: " + ch);
        }

        addToken(TokenType.VARIABLE);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseChar() {
        while (true) {
            char ch = getCurrentCharAndGoToNext();
            if (ch == '\\') {
                ch = getCurrentCharAndGoToNext();

                if (!CharUtils.isAnyOf(ch, CharUtils.EOF, CharUtils.NEW_LINE)) {
                    continue;
                }
            }

            if (CharUtils.isAnyOf(ch, CharUtils.EOF, CharUtils.NEW_LINE)) {
                return parseError("unclosed character constant");
            }

            if (ch == '\'') {
                break;
            }
        }

        addToken(TokenType.CHAR_CONSTANT);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseDot() {
        char ch = getCurrentChar();
        if (ch < '0' || '9' < ch) {
            return this::parseField;
        }

        return this::parseIdentifier;
    }

    private State parseField() {
        if (isPosAtWordTerminator()) {
            addToken(TokenType.DOT);
            moveStartToPos();

            return this::parseInsideAction;
        }

        char ch = goUntil(c -> !CharUtils.isAlphabetic(c));
        if (!isPosAtWordTerminator()) {
            return () -> parseError("bad character: " + ch);
        }

        addToken(TokenType.FIELD);
        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseNumber() {
        lookForNumber();

        char ch = getCurrentChar();
        if (CharUtils.isAlphabetic(ch)) {
            pos++;
            return parseError("bad number: " + getText());
        }

        if (CharUtils.isAnyOf(ch, "+-")) {
            lookForNumber();

            addToken(TokenType.COMPLEX);
        } else {
            addToken(TokenType.NUMBER);
        }

        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseIdentifier() {
        while (true) {
            char ch = getCurrentCharAndGoToNext();
            if (ch == CharUtils.EOF) {
                break;
            }
            if (!CharUtils.isAlphabetic(ch)) {
                pos--;
                break;
            }
        }
        String word = getText();

        if (KEY_MAP.containsKey(word)) {
            addToken(KEY_MAP.get(word));
        } else if (word.charAt(0) == '.') {
            addToken(TokenType.FIELD);
        } else if ("true".equals(word) || "false".equals(word)) {
            addToken(TokenType.BOOL);
        } else {
            addToken(TokenType.IDENTIFIER);
        }

        moveStartToPos();

        return this::parseInsideAction;
    }

    private State parseError(String error) {
        startLine = line;
        int column = pos - lineStart + 1;
        String errorMsg = String.format("%s at line %d, column %d", error, line, column);
        addToken(TokenType.ERROR, errorMsg);
        return null;
    }

    private void lookForNumber() {
        goIf("+-");

        String digits = CharUtils.DECIMAL_DIGITS;

        char ch = getCurrentCharAndGoToNext();
        if (ch == '0') {
            ch = getCurrentChar();
            if (CharUtils.isAnyOf(ch, "xX")) {
                digits = CharUtils.HEX_DIGITS;
                pos++;
            } else if (CharUtils.isAnyOf(ch, "oO")) {
                digits = CharUtils.OCTET_DIGITS;
                pos++;
            } else if (CharUtils.isAnyOf(ch, "bB")) {
                digits = CharUtils.BINARY_DIGITS;
                pos++;
            }
        }

        ch = goUntilNot(digits);
        if (ch == '.') {
            pos++;
            ch = goUntilNot(digits);
        }

        if (digits.length() == 10 + 1 && CharUtils.isAnyOf(ch, "eE")) {
            pos++;

            goIf("+-");
            ch = goUntilNot(CharUtils.DECIMAL_DIGITS);
        }

        if (digits.length() == 16 + 6 + 1 && CharUtils.isAnyOf(ch, "pP")) {
            pos++;

            goIf("+-");
            goUntilNot(CharUtils.DECIMAL_DIGITS);
        }

        goIf("i");
    }

    private void moveStartToPos() {
        start = pos;
        startLine = line;
    }

    private void movePosToStart() {
        pos = start;
    }

    private void goIf(CharSequence chars) {
        char ch = getCurrentChar();
        if (CharUtils.isAnyOf(ch, chars)) {
            pos++;
        }
    }

    private char goUntilNot(CharSequence chars) {
        return goUntil(ch -> !CharUtils.isAnyOf(ch, chars));
    }

    private char goUntil(Predicate<Character> predicate) {
        while (true) {
            char ch = getCurrentChar();

            if (CharUtils.isNewline(ch)) {
                addLine();
            }

            if (predicate.test(ch)) {
                return ch;
            }

            pos++;
        }
    }

    private char getCurrentCharAndGoToNext() {
        if (pos < input.length()) {
            char ch = input.charAt(pos);
            pos++;
            return ch;
        } else {
            return CharUtils.EOF;
        }
    }

    private char getCurrentChar() {
        return pos < input.length() ? input.charAt(pos) : CharUtils.EOF;
    }

    private boolean isPosAtLeftDelimWithTrimMarker() {
        int leftDelimLength = leftDelimiter.length();
        if (pos + leftDelimLength + 1 > input.length()) {
            return false;
        }

        if (input.indexOf(leftDelimiter, pos) != pos) {
            return false;
        }

        if (TRIM_MARKER != input.charAt(pos + leftDelimLength)) {
            return false;
        }

        return true;
    }

    private boolean isPosAtRightDelim() {
        return isPosAtRightDelimWithTrimMarker() || isPosAtRightDelimWithoutTrimMarker();
    }

    private boolean isPosAtRightDelimWithTrimMarker() {
        if (pos + 1 + rightDelimiter.length() > input.length()) {
            return false;
        }

        if (TRIM_MARKER != input.charAt(pos)) {
            return false;
        }

        return input.indexOf(rightDelimiter, pos + 1) == pos + 1;
    }

    private boolean isPosAtRightDelimWithoutTrimMarker() {
        if (pos + rightDelimiter.length() > input.length()) {
            return false;
        }

        // We're at a right delimiter without trim if:
        // 1. The delimiter is at current position
        // 2. There's no trim marker at current position
        return input.indexOf(rightDelimiter, pos) == pos &&
                (pos >= input.length() || input.charAt(pos) != TRIM_MARKER);
    }

    private boolean isPosAtRightDelim(int pos) {
        return input.indexOf(rightDelimiter, pos) == pos;
    }

    private boolean isPosAtWordTerminator() {
        char ch = getCurrentChar();
        if (CharUtils.isSpace(ch) || CharUtils.isAnyOf(ch, CharUtils.EOF + ".,|:()")) {
            return true;
        }
        return isPosAtRightDelim();
    }

    private String getText() {
        return input.substring(start, pos);
    }

    private void addLine() {
        line++;
        lineStart = pos + 1;
    }

    private void addToken(TokenType type) {
        addToken(type, getText(), start, line, start - lineStart + 1);
    }

    private void addToken(TokenType type, String value) {
        addToken(type, value, start, startLine, start - lineStart + 1);
    }

    private void addToken(TokenType type, String text, int start, int startLine, int column) {
        Token token = new Token(type, text, start, startLine, column);
        tokens.add(token);
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public String getLeftDelimiter() {
        return leftDelimiter;
    }

    public String getRightDelimiter() {
        return rightDelimiter;
    }

    private interface State {

        State run();
    }
}
