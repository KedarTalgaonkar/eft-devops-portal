package com.euronet.eft.portal.sbom;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.integration.sbom.SbomScanner;
import com.euronet.eft.portal.integration.sbom.SbomScanner.ScanOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SbomService {

    private static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private final SbomScanRepository scans;
    private final SbomScanner scanner;

    public SbomService(SbomScanRepository scans, SbomScanner scanner) {
        this.scans = scans;
        this.scanner = scanner;
    }

    private static ScanView toView(SbomScan s) {
        return new ScanView(s.getId(), s.getProjectName(), s.getProjectVersion(), s.getFormat(), s.getGenerator(),
            s.getStatus(), s.getComponentCount(), s.getCritical(), s.getHigh(), s.getMedium(), s.getLow(),
            s.getScannedAt());
    }

    public List<ScanView> recentScans() {
        return scans.findTop100ByOrderByScannedAtDesc().stream().map(SbomService::toView).toList();
    }

    public SbomSummary summary() {
        java.util.Map<String, SbomScan> latest = new java.util.LinkedHashMap<>();
        for (SbomScan s : scans.findTop100ByOrderByScannedAtDesc()) {
            latest.putIfAbsent(s.getProjectName(), s);
        }
        List<ProjectRisk> risks = latest.values().stream()
            .map(s -> new ProjectRisk(s.getProjectName(), s.getProjectVersion(), s.getId(), s.getCritical(), s.getHigh(),
                s.getMedium(), s.getLow(), s.getComponentCount(), s.getScannedAt()))
            .sorted(Comparator.comparingInt(ProjectRisk::critical).thenComparingInt(ProjectRisk::high).reversed())
            .toList();
        java.util.Map<String, ComponentRisk> byVuln = new java.util.LinkedHashMap<>();
        for (SbomScan s : latest.values()) {
            for (SbomVulnerability v : s.getVulnerabilities()) {
                ComponentRisk existing = byVuln.get(v.getVulnId() + "|" + v.getComponent());
                List<String> projects = new java.util.ArrayList<>(existing == null ? List.of() : existing.projects());
                projects.add(s.getProjectName());
                byVuln.put(v.getVulnId() + "|" + v.getComponent(), new ComponentRisk(v.getVulnId(), v.getSeverity(),
                    v.getComponent(), v.getVersion(), v.getFixedVersion(), v.getTitle(), projects));
            }
        }
        List<ComponentRisk> top = byVuln.values().stream()
            .sorted(Comparator.comparingInt((ComponentRisk c) -> SEVERITY_ORDER.indexOf(c.severity()))
                .thenComparing(c -> -c.projects().size()))
            .limit(15)
            .toList();
        return new SbomSummary(risks.size(),
            risks.stream().mapToInt(ProjectRisk::critical).sum(), risks.stream().mapToInt(ProjectRisk::high).sum(),
            risks.stream().mapToInt(ProjectRisk::medium).sum(), risks.stream().mapToInt(ProjectRisk::low).sum(),
            risks, top);
    }

    public List<VulnerabilityView> vulnerabilities(long scanId) {
        SbomScan scan = scans.findById(scanId).orElseThrow(() -> new NotFoundException("SBOM scan", scanId));
        return scan.getVulnerabilities().stream()
            .sorted(Comparator.comparingInt((SbomVulnerability v) -> SEVERITY_ORDER.indexOf(v.getSeverity()))
                .thenComparing(SbomVulnerability::getComponent))
            .map(v -> new VulnerabilityView(v.getVulnId(), v.getSeverity(), v.getComponent(), v.getVersion(),
                v.getFixedVersion(), v.getTitle()))
            .toList();
    }

    @Transactional
    public ScanView scan(ScanRequest request) {
        ScanOutcome outcome = scanner.scan(request.projectName().strip(), request.projectVersion().strip());
        SbomScan scan = new SbomScan(request.projectName().strip(), request.projectVersion().strip(),
            outcome.format(), outcome.generator(), outcome.componentCount());
        outcome.findings().forEach(f -> scan.addVulnerability(new SbomVulnerability(
            f.vulnId(), f.severity().name(), f.component(), f.version(), f.fixedVersion(), f.title())));
        return toView(scans.save(scan));
    }

    public record ScanRequest(@NotBlank @Size(max = 200) String projectName,
                              @NotBlank @Size(max = 100) String projectVersion) {
    }

    public record ScanView(Long id, String projectName, String projectVersion, String format, String generator,
                           String status, int componentCount, int critical, int high, int medium, int low,
                           Instant scannedAt) {
    }

    public record VulnerabilityView(String vulnId, String severity, String component, String version,
                                    String fixedVersion, String title) {
    }

    public record ProjectRisk(String projectName, String projectVersion, Long scanId, int critical, int high,
                              int medium, int low, int componentCount, Instant scannedAt) {
    }

    public record ComponentRisk(String vulnId, String severity, String component, String version, String fixedVersion,
                                String title, List<String> projects) {
    }

    /**
     * Current risk: the latest scan of every project, totals, and the vulnerabilities that affect most projects.
     */
    public record SbomSummary(int projects, int critical, int high, int medium, int low,
                              List<ProjectRisk> projectRisks, List<ComponentRisk> topComponents) {
    }
}
