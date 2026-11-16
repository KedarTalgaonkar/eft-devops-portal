package com.euronet.eft.portal.integration.sbom;

import com.euronet.eft.portal.integration.IntegrationHealth;

import java.util.List;

/**
 * Generates an SBOM for a project version and scans it for known vulnerabilities.
 * Live implementation (phase 3): CycloneDX generation plus Trivy or Dependency-Track scanning.
 */
public interface SbomScanner extends IntegrationHealth {

    ScanOutcome scan(String projectName, String projectVersion);

    enum Severity {CRITICAL, HIGH, MEDIUM, LOW}

    record Finding(String vulnId, Severity severity, String component, String version, String fixedVersion,
                   String title) {
    }

    record ScanOutcome(String format, String generator, int componentCount, List<Finding> findings) {
    }
}
