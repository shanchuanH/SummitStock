package com.example.portfolio.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CiWorkflowContractTest {
    @Test
    void requiredGateCoversEveryReleasePartitionAndUsesFullSecretScanHistory() throws Exception {
        var workflow = Files.readString(Path.of("..", "..", ".github", "workflows", "ci.yml"));

        assertThat(workflow)
                .contains(
                        "java-version: \"25\"",
                        "openapi:",
                        "e2e-desktop:",
                        "e2e-mobile:",
                        "supply-chain:",
                        "gitleaks:",
                        "fetch-depth: 0",
                        "docker:",
                        "needs: [backend, frontend, openapi, e2e-desktop, e2e-mobile, supply-chain, gitleaks, docker]");
        assertThat(workflow).contains("test \"$GITLEAKS\" = success", "test \"$DOCKER\" = success");
    }
}
