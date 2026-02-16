package org.alexmond.jhelm.gotemplate.internal.util;

import org.alexmond.jhelm.gotemplate.internal.util.CharUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CharUtilsTest {

    @Test
    void unquoteChar() {
        assertThrows(IllegalArgumentException.class, () -> CharUtils.unquoteChar("\\'x"));
        assertThrows(IllegalArgumentException.class, () -> CharUtils.unquoteChar("'xx'"));
    }

}