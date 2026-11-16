package com.euronet.eft.portal.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request and response shapes of the inventory API. Environments are codes from /api/environments.
 */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record ApplicationSummary(
        Long id, String name, String client, String owner,
        int deploymentCount, List<String> environments, List<String> repositories, Instant updatedAt) {
    }

    public record ApplicationDetail(
        Long id, String name, String client, String owner, Instant updatedAt, List<String> repositories,
        List<DeploymentView> deployments) {
    }

    public record DeploymentView(
        Long id,
        Long applicationId,
        String application,
        String client,
        String owner,
        String environment,
        String platform,
        String contextName,
        String baseUrl,
        String contextUrl,
        String tomcatVersion,
        String javaVersion,
        boolean tomcatAdminDone,
        boolean buildXmlDone,
        String jenkinsJob,
        String effectiveJenkinsJob,
        List<String> repositories,
        Instant updatedAt) {
    }

    /**
     * Only the Jenkins job is editable per deployment; everything else comes from the Excel import.
     */
    public record UpdateDeploymentRequest(@Size(max = 300) String jenkinsJob) {
    }

    public record UpdateRepositoriesRequest(
        @NotNull @Size(max = 50) List<@Size(min = 1, max = 200) String> repositories) {
    }

    public record ImportResult(
        String fileName,
        int sheetsRead,
        int rowsRead,
        int applicationsCreated,
        int applicationsUpdated,
        int deploymentsCreated,
        int deploymentsUpdated,
        List<String> newEnvironments,
        List<String> warnings) {
    }

    public record ChangeView(
        Long id, Long applicationId, String application, String client, Long deploymentId, String environment,
        String contextName, String changeType, String field, String oldValue, String newValue, String source,
        String changedBy, Instant changedAt) {
    }
}
