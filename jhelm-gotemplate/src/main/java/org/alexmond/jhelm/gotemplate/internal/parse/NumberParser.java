package org.alexmond.jhelm.gotemplate.internal.parse;

import org.alexmond.jhelm.gotemplate.TemplateParseException;
import org.alexmond.jhelm.gotemplate.internal.util.CharUtils;
import org.alexmond.jhelm.gotemplate.internal.util.Complex;

/**
 * Parses numeric tokens (integers, floats, complex numbers, character constants) into
 * {@link NumberNode} AST nodes.
 */
final class NumberParser {

	private NumberParser() {
	}

	static NumberNode parse(Token token) throws TemplateParseException {
		NumberNode numberNode = new NumberNode(token.value());
		parseInto(numberNode, token);
		return numberNode;
	}

	private static void parseInto(NumberNode numberNode, Token token) throws TemplateParseException {
		String text = token.value();
		TokenType type = token.type();
		if (type == TokenType.CHAR_CONSTANT) {
			parseCharConstant(numberNode, text);
			return;
		}

		if (type == TokenType.COMPLEX) {
			try {
				Complex complex = Complex.parseComplex(text);
				numberNode.setIsComplex(true);
				numberNode.setComplex(complex);
				simplifyComplex(numberNode, complex);
				return;
			}
			catch (NumberFormatException ignored) {
			}
		}

		int length = text.length();
		if (length > 0 && text.charAt(length - 1) == 'i') {
			try {
				Complex complex = Complex.parseComplex(text);
				numberNode.setIsComplex(true);
				numberNode.setComplex(complex);
				simplifyComplex(numberNode, complex);
				return;
			}
			catch (NumberFormatException ignored) {
			}
		}

		try {
			long intValue = parseIntValue(text);
			numberNode.setIsInt(true);
			numberNode.setIntValue(intValue);
			numberNode.setIsFloat(true);
			numberNode.setFloatValue(intValue);
		}
		catch (NumberFormatException ignored) {
			try {
				double floatValue = parseFloatValue(text);
				numberNode.setIsFloat(true);
				numberNode.setFloatValue(floatValue);
				simplifyFloat(numberNode, floatValue);
			}
			catch (NumberFormatException ignoredAgain) { // NOPMD EmptyCatchBlock - not a
															// float either; falls through
															// to validation below
			}
		}

		if (!numberNode.isInt() && !numberNode.isFloat() && !numberNode.isComplex()) {
			throw new TemplateParseException(String.format("illegal number syntax: %s, line: %d, column: %d", text,
					token.line(), token.column()));
		}
	}

	private static void parseCharConstant(NumberNode numberNode, String text) throws TemplateParseException {
		if (text.charAt(0) != '\'') {
			throw new TemplateParseException(String.format("malformed character constant: %s", text));
		}

		int ch;
		try {
			ch = CharUtils.unquoteChar(text);
		}
		catch (IllegalArgumentException ex) {
			throw new TemplateParseException("invalid syntax: " + text, ex);
		}

		numberNode.setIsInt(true);
		numberNode.setIntValue(ch);
		numberNode.setIsFloat(true);
		numberNode.setFloatValue(ch);
	}

	private static long parseIntValue(String text) {
		boolean signed = false;
		boolean negative = false;

		char firstChar = text.charAt(0);
		if (firstChar == '+') {
			signed = true;
		}
		if (firstChar == '-') {
			signed = true;
			negative = true;
		}

		if (signed && text.length() > 1) {
			char secondChar = text.charAt(1);
			if (secondChar == '+' || secondChar == '-') {
				throw new NumberFormatException("invalid number: multiple sign characters");
			}
		}

		long intValue = parseIntWithBase(text, signed);
		return (negative) ? -intValue : intValue;
	}

	private static long parseIntWithBase(String text, boolean signed) {
		int offset = (signed) ? 1 : 0;
		String trimmed = trimUnderscore(text);

		if (text.startsWith("0b", offset) || text.startsWith("0B", offset)) {
			return Long.parseLong(trimmed.substring(offset + 2), 2);
		}

		if (text.startsWith("0o", offset) || text.startsWith("0O", offset)) {
			return Long.parseLong(trimmed.substring(offset + 2), 8);
		}

		if (text.startsWith("0x", offset) || text.startsWith("0X", offset)) {
			return Long.parseLong(trimmed.substring(offset + 2), 16);
		}

		return Long.parseLong(trimmed.substring(offset), 10);
	}

	private static double parseFloatValue(String text) {
		return Double.parseDouble(trimUnderscore(text));
	}

	private static void simplifyComplex(NumberNode numberNode, Complex complex) {
		if (complex.getImaginary() == 0) {
			double floatValue = complex.getReal();
			numberNode.setIsFloat(true);
			numberNode.setFloatValue(floatValue);
			simplifyFloat(numberNode, floatValue);
		}
	}

	private static void simplifyFloat(NumberNode numberNode, double floatValue) {
		long intValue = (long) floatValue;
		if (floatValue == intValue) {
			numberNode.setIsInt(true);
			numberNode.setIntValue(intValue);
		}
	}

	private static String trimUnderscore(String text) {
		return text.replace("_", "");
	}

}
