package org.alexmond.jhelm.gotemplate.helm;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.GoTemplateFactory;
import org.alexmond.jhelm.gotemplate.helm.chart.ChartFunctions;
import org.alexmond.jhelm.gotemplate.helm.conversion.ConversionFunctions;
import org.alexmond.jhelm.gotemplate.helm.kubernetes.KubernetesFunctions;
import org.alexmond.jhelm.gotemplate.helm.kubernetes.KubernetesProvider;
import org.alexmond.jhelm.gotemplate.helm.template.TemplateFunctions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinator class for all Helm-specific template functions.
 * Organizes functions by category: template, conversion, kubernetes, and chart operations.
 *
 * @see <a href="https://helm.sh/docs/chart_template_guide/function_list/">Helm Template Functions</a>
 */
public class HelmFunctions {

    /**
     * Get all Helm functions from all categories with Kubernetes provider.
     * Use this when Kubernetes API access is available.
     *
     * @param factory            The GoTemplateFactory instance for template operations
     * @param kubernetesProvider Provider for Kubernetes API access (can be null)
     * @return Map of function name to Function implementation
     */
    public static Map<String, Function> getFunctions(GoTemplateFactory factory, KubernetesProvider kubernetesProvider) {
        Map<String, Function> functions = new HashMap<>();

        // Template operations (include, tpl, required)
        functions.putAll(TemplateFunctions.getFunctions(factory));

        // YAML/JSON conversion (toYaml, toJson, fromYaml, fromJson, and must* variants)
        functions.putAll(ConversionFunctions.getFunctions());

        // Kubernetes operations (lookup, kubeVersion) - with provider
        functions.putAll(KubernetesFunctions.getFunctions(kubernetesProvider));

        // Chart operations (semverCompare, certificate generation)
        functions.putAll(ChartFunctions.getFunctions());

        return functions;
    }

    /**
     * Get all Helm functions from all categories without Kubernetes provider.
     * Kubernetes functions will return stub data.
     *
     * @param factory The GoTemplateFactory instance for template operations
     * @return Map of function name to Function implementation
     */
    public static Map<String, Function> getFunctions(GoTemplateFactory factory) {
        return getFunctions(factory, null);
    }

    /**
     * Get function categories for documentation and introspection.
     *
     * @return Map of category name to list of function names
     */
    public static Map<String, List<String>> getFunctionCategories() {
        Map<String, List<String>> categories = new HashMap<>();

        categories.put("Template", List.of(
                "include", "mustInclude", "tpl", "mustTpl", "required"
        ));

        categories.put("Conversion", List.of(
                "toYaml", "toJson", "fromYaml", "fromJson", "fromYamlArray",
                "toToml", "fromToml",
                "mustToYaml", "mustToJson", "mustFromYaml", "mustFromJson",
                "mustToToml", "mustFromToml"
        ));

        categories.put("Kubernetes", List.of(
                "lookup", "kubeVersion"
        ));

        categories.put("Chart", List.of(
                "semverCompare", "semver",
                "genPrivateKey", "genCA", "genSignedCert", "genSelfSignedCert"
        ));

        return categories;
    }
}
