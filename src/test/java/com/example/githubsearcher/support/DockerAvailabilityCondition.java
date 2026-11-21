package com.example.githubsearcher.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Disables PostgreSQL integration tests when the Docker daemon is not reachable,
 * so {@code mvn test} still passes on machines without Docker.
 */
public class DockerAvailabilityCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker is available");
            }
        } catch (Throwable ignored) {
            // Daemon missing, misconfigured, or the client failed to start.
        }
        return ConditionEvaluationResult.disabled(
                "Docker is not available; PostgreSQL integration tests are skipped");
    }
}
