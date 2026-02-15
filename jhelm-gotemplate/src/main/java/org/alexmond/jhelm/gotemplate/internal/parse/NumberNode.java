package org.alexmond.jhelm.gotemplate.internal.parse;

import lombok.Getter;
import lombok.Setter;
import org.alexmond.jhelm.gotemplate.internal.util.Complex;

@Getter
@Setter
public class NumberNode implements Node {

    private final String text;

    private boolean isInt;
    private long intValue;

    private boolean isFloat;
    private double floatValue;

    private boolean isComplex;
    private Complex complexValue;

    public NumberNode(String text) {
        this.text = text;
    }

    // Preserve original setter names used by Parser
    public void setIsInt(boolean isInt) {
        this.isInt = isInt;
    }

    public void setIsFloat(boolean isFloat) {
        this.isFloat = isFloat;
    }

    public void setIsComplex(boolean isComplex) {
        this.isComplex = isComplex;
    }

    @Override
    public String toString() {
        return text;
    }

    public void setComplex(Complex complex) {
        this.complexValue = complex;
    }
}
