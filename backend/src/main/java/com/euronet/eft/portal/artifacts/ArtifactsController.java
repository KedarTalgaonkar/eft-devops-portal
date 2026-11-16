package com.euronet.eft.portal.artifacts;

import com.euronet.eft.portal.integration.artifactory.ArtifactoryClient;
import com.euronet.eft.portal.integration.artifactory.ArtifactoryModels.ArtifactInfo;
import com.euronet.eft.portal.integration.artifactory.ArtifactoryModels.ArtifactRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/artifacts")
@Tag(name = "Artifacts (Artifactory)", description = "Repositories and published artifacts")
public class ArtifactsController {

    private final ArtifactoryClient artifactory;

    public ArtifactsController(ArtifactoryClient artifactory) {
        this.artifactory = artifactory;
    }

    @GetMapping("/repositories")
    @Operation(summary = "Repositories with artifact counts and size")
    public List<ArtifactRepository> repositories() {
        return artifactory.repositories();
    }

    @GetMapping("/repositories/{key}/artifacts")
    @Operation(summary = "Artifacts in one repository, newest first")
    public List<ArtifactInfo> artifacts(@PathVariable String key) {
        return artifactory.artifacts(key);
    }
}
