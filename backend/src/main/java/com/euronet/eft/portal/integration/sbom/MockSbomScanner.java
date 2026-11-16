package com.euronet.eft.portal.integration.sbom;

import com.euronet.eft.portal.integration.IntegrationStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(prefix = "portal.integrations.sbom", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockSbomScanner implements SbomScanner {

    /**
     * Typical findings in older Tomcat/Java 8 web apps (well-known public advisories).
     */
    private static final List<Finding> CATALOGUE = List.of(
        new Finding("CVE-2021-44228", Severity.CRITICAL, "org.apache.logging.log4j:log4j-core", "2.14.1", "2.17.1", "Log4Shell remote code execution"),
        new Finding("CVE-2022-22965", Severity.CRITICAL, "org.springframework:spring-beans", "5.2.8.RELEASE", "5.2.20.RELEASE", "Spring4Shell data binding RCE"),
        new Finding("CVE-2022-42889", Severity.CRITICAL, "org.apache.commons:commons-text", "1.9", "1.10.0", "Text4Shell interpolation RCE"),
        new Finding("CVE-2020-36518", Severity.HIGH, "com.fasterxml.jackson.core:jackson-databind", "2.10.0", "2.12.6.1", "Deeply nested JSON denial of service"),
        new Finding("CVE-2022-1471", Severity.HIGH, "org.yaml:snakeyaml", "1.26", "2.0", "Unsafe deserialization in Constructor"),
        new Finding("CVE-2023-24998", Severity.HIGH, "commons-fileupload:commons-fileupload", "1.4", "1.5", "Unlimited multipart parts (DoS)"),
        new Finding("CVE-2021-29425", Severity.MEDIUM, "commons-io:commons-io", "2.6", "2.7", "Path traversal in FileNameUtils.normalize"),
        new Finding("CVE-2020-13956", Severity.MEDIUM, "org.apache.httpcomponents:httpclient", "4.5.12", "4.5.13", "Incorrect request URI handling"),
        new Finding("CVE-2023-2976", Severity.MEDIUM, "com.google.guava:guava", "30.1-jre", "32.0.0-jre", "Insecure temp directory"),
        new Finding("CVE-2020-8908", Severity.LOW, "com.google.guava:guava", "28.2-jre", "30.0-jre", "Temp directory permissions"),
        new Finding("CVE-2023-34462", Severity.MEDIUM, "io.netty:netty-handler", "4.1.86.Final", "4.1.94.Final", "SniHandler memory allocation DoS"));

    @Override
    public ScanOutcome scan(String projectName, String projectVersion) {
        Random r = new Random((projectName + "@" + projectVersion).hashCode());
        List<Finding> findings = new ArrayList<>();
        for (Finding f : CATALOGUE) {
            if (r.nextInt(3) == 0) {
                findings.add(f);
            }
        }
        return new ScanOutcome("CycloneDX 1.6", "mock-scanner", 60 + r.nextInt(180), findings);
    }

    @Override
    public IntegrationStatus status() {
        return IntegrationStatus.mock("sbom", "SBOM scanner");
    }
}
