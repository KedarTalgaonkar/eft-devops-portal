package com.euronet.eft.portal.integration.sonar;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.integration.IntegrationStatus;
import com.euronet.eft.portal.integration.sonar.SonarModels.GateCondition;
import com.euronet.eft.portal.integration.sonar.SonarModels.GateStatus;
import com.euronet.eft.portal.integration.sonar.SonarModels.QualityProject;
import com.euronet.eft.portal.integration.sonar.SonarModels.QualityProjectDetail;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(prefix = "portal.integrations.sonar", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockSonarClient implements SonarClient {

    private static final List<String> PROJECTS = List.of(
        "ppi-branch", "ppi-activation-api", "ppi-customer-api", "prepaid-qr-api", "prepaid-axis", "ria-portal",
        "imps-portal", "rupay-ecom", "e-mandate", "transakto-engine", "transakto-admin-portal", "qr-engine", "recon");
    private static final String[] RATINGS = {"A", "A", "A", "B", "B", "C", "D"};

    private static GateCondition condition(String metric, String op, String threshold, double actual) {
        return new GateCondition(metric, op, threshold, String.valueOf(actual),
            actual < Double.parseDouble(threshold) ? GateStatus.ERROR : GateStatus.OK);
    }

    @Override
    public List<QualityProject> projects() {
        return PROJECTS.stream().map(this::build).toList();
    }

    @Override
    public QualityProjectDetail project(String key) {
        if (!PROJECTS.contains(key)) {
            throw new NotFoundException("SonarQube project", key);
        }
        QualityProject p = build(key);
        List<GateCondition> conditions = List.of(
            condition("new_coverage", "LT", "80", p.coverage()),
            new GateCondition("new_duplicated_lines_density", "GT", "3", String.valueOf(p.duplication()),
                p.duplication() > 3 ? GateStatus.ERROR : GateStatus.OK),
            new GateCondition("new_reliability_rating", "GT", "A", p.reliabilityRating(),
                "A".equals(p.reliabilityRating()) ? GateStatus.OK : GateStatus.ERROR),
            new GateCondition("new_security_rating", "GT", "A", p.securityRating(),
                "A".equals(p.securityRating()) ? GateStatus.OK : GateStatus.ERROR));
        return new QualityProjectDetail(p, conditions);
    }

    private QualityProject build(String key) {
        Random r = new Random(key.hashCode());
        double coverage = Math.round((35 + r.nextDouble() * 60) * 10) / 10.0;
        double duplication = Math.round(r.nextDouble() * 9 * 10) / 10.0;
        String reliability = RATINGS[r.nextInt(RATINGS.length)];
        String security = RATINGS[r.nextInt(RATINGS.length)];
        GateStatus gate = coverage < 80 || duplication > 3 || !"A".equals(reliability) || !"A".equals(security)
            ? (r.nextInt(3) == 0 ? GateStatus.WARN : GateStatus.ERROR)
            : GateStatus.OK;
        if (r.nextInt(4) == 0) {
            gate = GateStatus.OK;
        }
        return new QualityProject(key, key, gate, r.nextInt(25), r.nextInt(12), r.nextInt(20), 40 + r.nextInt(900),
            coverage, duplication, reliability, security, RATINGS[r.nextInt(RATINGS.length)],
            5_000 + r.nextInt(120_000), Instant.now().truncatedTo(ChronoUnit.HOURS).minus(r.nextInt(240), ChronoUnit.HOURS),
            "https://sonarqube.example.internal/dashboard?id=" + key);
    }

    @Override
    public IntegrationStatus status() {
        return IntegrationStatus.mock("sonar", "SonarQube");
    }
}
