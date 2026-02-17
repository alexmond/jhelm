package org.alexmond.jhelm.gotemplate.internal.util;

import org.alexmond.jhelm.gotemplate.internal.util.CharUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CharUtilsTest {

    @Test
    void unquoteChar() {
        assertThrows(IllegalArgumentException.class, () -> CharUtils.unquoteChar("\\'x"));
        assertThrows(IllegalArgumentException.class, () -> CharUtils.unquoteChar("'xx'"));
    }

    static Stream<Arguments> validCharProvider() {
        return Stream.of(
            Arguments.of("'a'", 'a'),
            Arguments.of("'z'", 'z'),
            Arguments.of("'1'", '1')
        );
    }

    @ParameterizedTest
    @MethodSource("validCharProvider")
    void unquoteCharValid(String input, char expected) {
        // Valid single character - length 3
        assertEquals(expected, CharUtils.unquoteChar(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'abc'", "'ab'"})
    void unquoteCharInvalidMultipleChars(String input) {
        // Length > 3 with non-backslash first char should throw
        assertThrows(IllegalArgumentException.class, () -> CharUtils.unquoteChar(input));
    }

}