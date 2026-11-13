package com.euronet.eft.portal.integration.sonar;

import java.time.Instant;
import java.util.List;

public final class SonarModels {

    private SonarModels() {
    }

    public enum GateStatus { OK, WARN, ERROR, NONE }

    /** Quality metrics of one SonarQube project (from /api/measures/component and /api/qualitygates/project_status). */
    public record QualityProject(
            String key,
            String name,
            GateStatus qualityGate,
            int bugs,
            int vulnerabilities,
            int securityHotspots,
            int codeSmells,
            double coverage,
            double duplication,
            String reliabilityRating,
            String securityRating,
            String maintainabilityRating,
            long linesOfCode,
            Instant lastAnalysis,
            String url) {
    }

    public record GateCondition(String metric, String comparator, String threshold, String actual, GateStatus status) {
    }

    public record QualityProjectDetail(QualityProject project, List<GateCondition> conditions) {
    }
}
