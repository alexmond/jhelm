package org.alexmond.jhelm.gotemplate.helm;

import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.FunctionProvider;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;

/**
 * {@link FunctionProvider} that contributes all Helm template functions: conversion
 * (toYaml, toJson, etc.), template (include, tpl, required), and Kubernetes (lookup,
 * kubeVersion).
 *
 * <p>
 * Discovered automatically via {@link java.util.ServiceLoader} when
 * {@code jhelm-gotemplate-helm} is on the classpath. Can also be registered explicitly
 * via {@link GoTemplate.Builder#withProvider(FunctionProvider)}.
 *
 * <p>
 * Priority is {@code 200} (overrides both Go builtins at 0 and Sprig at 100).
 *
 * <p>
 * No-arg constructor (used by ServiceLoader): Kubernetes functions return stub data.
 * Parameterized constructor: accepts a {@link KubernetesProvider} for real K8s API
 * access.
 *
 * @see HelmFunctions
 */
public class HelmFunctionProvider implements FunctionProvider {

	private final KubernetesProvider kubernetesProvider;

	/**
	 * Create a HelmFunctionProvider without Kubernetes API access. Kubernetes functions
	 * (lookup, kubeVersion) will return stub data. Used by ServiceLoader.
	 */
	public HelmFunctionProvider() {
		this(null);
	}

	/**
	 * Create a HelmFunctionProvider with Kubernetes API access.
	 * @param kubernetesProvider provider for Kubernetes API access (can be null)
	 */
	public HelmFunctionProvider(KubernetesProvider kubernetesProvider) {
		this.kubernetesProvider = kubernetesProvider;
	}

	@Override
	public Map<String, Function> getFunctions(GoTemplate template) {
		return HelmFunctions.getFunctions(template, kubernetesProvider);
	}

	@Override
	public int priority() {
		return 200;
	}

	@Override
	public String name() {
		return "Helm";
	}

}
