package com.euronet.eft.portal.security.access;

/**
 * The portal's modules, each guarded by group-based access. The keys are stored in {@code access_group_module}.
 */
public enum PortalModule {
    DASHBOARD("Command centre", "Overview, issues, environment status, service health, announcements, executive digest"),
    PIPELINES("Pipelines", "Jenkins builds, deployments and the AI build failure explainer"),
    SOURCE_CODE("Source code", "GitHub repositories, commits and pull requests"),
    ARTIFACTS("Artifacts", "Artifactory repositories and artifacts"),
    QUALITY("Code quality", "SonarQube quality gates and projects"),
    SBOM("SBOM & vulnerabilities", "Software bills of materials and vulnerability scans"),
    MONITORING("Monitoring", "Grafana / Prometheus alerts and infrastructure"),
    INVENTORY("Application inventory", "Applications, deployment paths, change log, environments");

    private final String label;
    private final String description;

    PortalModule(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
