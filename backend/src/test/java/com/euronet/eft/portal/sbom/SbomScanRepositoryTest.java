package com.euronet.eft.portal.sbom;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice against the real PostgreSQL schema created by Flyway.
 */
@Tag("db")
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SbomScanRepositoryTest {

    @Autowired
    private SbomScanRepository scans;

    @Test
    void storesScanWithVulnerabilitiesAndSeverityCounts() {
        SbomScan scan = new SbomScan("prepaid-qr-api", "1.4.2", "CycloneDX 1.6", "test", 120);
        scan.addVulnerability(new SbomVulnerability("CVE-2021-44228", "CRITICAL", "log4j-core", "2.14.1", "2.17.1", "Log4Shell"));
        scan.addVulnerability(new SbomVulnerability("CVE-2021-29425", "MEDIUM", "commons-io", "2.6", "2.7", "Path traversal"));
        scans.saveAndFlush(scan);

        SbomScan loaded = scans.findTop100ByOrderByScannedAtDesc().stream()
            .filter(s -> s.getId().equals(scan.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(loaded.getCritical()).isEqualTo(1);
        assertThat(loaded.getMedium()).isEqualTo(1);
        assertThat(loaded.getVulnerabilities()).hasSize(2);
    }
}
