package org.alexmond.jhelm.gotemplate.internal;

import java.io.IOException;
import java.io.Reader;

public final class IOUtils {

    private static final int EOF = -1;


    private IOUtils() {
    }

    public static String read(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();

        char[] buffer = new char[8192];
        int n;
        while (EOF != (n = reader.read(buffer))) {
            sb.append(buffer, 0, n);
        }

        return sb.toString();
    }
}
