package org.alexmond.jhelm.gotemplate;

@FunctionalInterface
public interface Function {

	Object invoke(Object... args);

}
