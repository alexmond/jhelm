package org.alexmond.jhelm.kube;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JUnit 5 condition annotation that skips tests when no Kubernetes cluster is available.
 * Tests annotated with this will only run when a cluster is reachable.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(KubeClusterAvailable.Condition.class)
public @interface KubeClusterAvailable {

    class Condition implements ExecutionCondition {
        private static volatile Boolean clusterAvailable;

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (clusterAvailable == null) {
                clusterAvailable = checkCluster();
            }
            return clusterAvailable
                    ? ConditionEvaluationResult.enabled("Kubernetes cluster is available")
                    : ConditionEvaluationResult.disabled("Kubernetes cluster is not available");
        }

        private static boolean checkCluster() {
            try {
                var client = io.kubernetes.client.util.Config.defaultClient();
                var versionApi = new io.kubernetes.client.openapi.apis.VersionApi(client);
                // Set a short timeout for the check
                client.setConnectTimeout(2000);
                client.setReadTimeout(2000);
                versionApi.getCode().execute();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
