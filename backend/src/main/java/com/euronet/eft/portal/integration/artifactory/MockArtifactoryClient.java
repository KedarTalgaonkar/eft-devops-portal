package com.euronet.eft.portal.integration.artifactory;

import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.integration.IntegrationStatus;
import com.euronet.eft.portal.integration.artifactory.ArtifactoryModels.ArtifactInfo;
import com.euronet.eft.portal.integration.artifactory.ArtifactoryModels.ArtifactRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(prefix = "portal.integrations.artifactory", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockArtifactoryClient implements ArtifactoryClient {

    private static final List<Repo> REPOS = List.of(
        new Repo("eft-libs-release-local", "LOCAL", "maven", "Released WAR/JAR builds",
            List.of("ppibranch", "PPIPrepaidAPI", "imps_portal", "E_Mandate", "TransaktoEngine", "riaapi")),
        new Repo("eft-libs-snapshot-local", "LOCAL", "maven", "Snapshot builds from DEVELOP",
            List.of("ppibranch", "ppicustomerapis", "SITCBCQRAPI", "RupayEcom")),
        new Repo("eft-docker-local", "LOCAL", "docker", "Container images",
            List.of("eft-devops-portal-backend", "eft-devops-portal-frontend")),
        new Repo("maven-remote", "REMOTE", "maven", "Proxy of Maven Central", List.of("spring-core", "poi-ooxml")),
        new Repo("eft-maven", "VIRTUAL", "maven", "Virtual: release + snapshot + remote", List.of()));

    @Override
    public List<ArtifactRepository> repositories() {
        return REPOS.stream().map(repo -> {
            List<ArtifactInfo> items = artifacts(repo.key());
            long size = items.stream().mapToLong(ArtifactInfo::sizeBytes).sum();
            Instant last = items.stream().map(ArtifactInfo::createdAt).max(Comparator.naturalOrder()).orElse(null);
            return new ArtifactRepository(repo.key(), repo.type(), repo.packageType(), repo.description(), items.size(),
                size, last, "https://artifactory.example.internal/ui/repos/tree/General/" + repo.key());
        }).toList();
    }

    @Override
    public List<ArtifactInfo> artifacts(String repositoryKey) {
        Repo repo = REPOS.stream().filter(r -> r.key().equals(repositoryKey)).findFirst()
            .orElseThrow(() -> new NotFoundException("Artifactory repository", repositoryKey));
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        List<ArtifactInfo> out = new ArrayList<>();
        for (String module : repo.modules()) {
            Random r = new Random((repo.key() + module).hashCode());
            int versions = 2 + r.nextInt(5);
            for (int v = versions; v >= 1; v--) {
                boolean snapshot = repo.key().contains("snapshot");
                String version = "1." + (r.nextInt(5)) + "." + v + (snapshot ? "-SNAPSHOT" : "");
                String ext = repo.packageType().equals("docker") ? "manifest.json" : module + "-" + version + ".war";
                byte[] hash = new byte[32];
                r.nextBytes(hash);
                out.add(new ArtifactInfo(repo.key(), "com/euronet/eft/" + module + "/" + version, ext, version,
                    (repo.packageType().equals("docker") ? 80 : 20) * 1_000_000L + r.nextInt(30_000_000),
                    now.minus((long) (versions - v) * (24 + r.nextInt(240)), ChronoUnit.HOURS),
                    "jenkins", r.nextInt(300), HexFormat.of().formatHex(hash)));
            }
        }
        out.sort(Comparator.comparing(ArtifactInfo::createdAt).reversed());
        return out;
    }

    @Override
    public IntegrationStatus status() {
        return IntegrationStatus.mock("artifactory", "Artifactory");
    }

    private record Repo(String key, String type, String packageType, String description, List<String> modules) {
    }
}
