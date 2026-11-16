package com.euronet.eft.portal.sbom;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sbom_scan")
public class SbomScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "project_version", nullable = false)
    private String projectVersion;

    @Column(nullable = false)
    private String format;

    @Column(nullable = false)
    private String generator;

    @Column(nullable = false)
    private String status;

    @Column(name = "component_count", nullable = false)
    private int componentCount;

    private int critical;
    private int high;
    private int medium;
    private int low;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SbomVulnerability> vulnerabilities = new ArrayList<>();

    protected SbomScan() {
    }

    public SbomScan(String projectName, String projectVersion, String format, String generator, int componentCount) {
        this.projectName = projectName;
        this.projectVersion = projectVersion;
        this.format = format;
        this.generator = generator;
        this.componentCount = componentCount;
        this.status = "COMPLETED";
        this.scannedAt = Instant.now();
    }

    public void addVulnerability(SbomVulnerability v) {
        v.setScan(this);
        vulnerabilities.add(v);
        switch (v.getSeverity()) {
            case "CRITICAL" -> critical++;
            case "HIGH" -> high++;
            case "MEDIUM" -> medium++;
            default -> low++;
        }
    }

    public Long getId() {
        return id;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public String getFormat() {
        return format;
    }

    public String getGenerator() {
        return generator;
    }

    public String getStatus() {
        return status;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public int getCritical() {
        return critical;
    }

    public int getHigh() {
        return high;
    }

    public int getMedium() {
        return medium;
    }

    public int getLow() {
        return low;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public List<SbomVulnerability> getVulnerabilities() {
        return vulnerabilities;
    }
}
