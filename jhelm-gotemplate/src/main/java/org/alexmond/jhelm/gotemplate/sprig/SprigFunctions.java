package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.Function;

import org.alexmond.jhelm.gotemplate.sprig.functions.SprigFunctionsLegacy;

import java.util.Map;

/**
 * Bridge class for Sprig functions - delegates to SprigFunctionsRegistry for refactored functions
 * and SprigFunctionsLegacy for functions that haven't been refactored yet
 *
 * @deprecated Use SprigFunctionsRegistry.getAllFunctions() directly
 */
public class SprigFunctions {

    /**
     * Get all Sprig functions
     *
     * @return Map of function name to Function implementation
     */
    public static Map<String, Function> getFunctions() {
        return SprigFunctionsRegistry.getAllFunctions();
    }

    /**
     * Get legacy functions that haven't been refactored into category-specific classes yet
     * This includes: math, encoding, crypto, date, reflection, network functions
     *
     * @return Map of legacy function name to Function implementation
     */
    public static Map<String, Function> getLegacyFunctions() {
        return SprigFunctionsLegacy.getFunctions();
    }
}
