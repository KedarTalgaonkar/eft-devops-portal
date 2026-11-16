package com.euronet.eft.portal.integration.artifactory;

import com.euronet.eft.portal.integration.IntegrationHealth;
import com.euronet.eft.portal.integration.artifactory.ArtifactoryModels.ArtifactInfo;
import com.euronet.eft.portal.integration.artifactory.ArtifactoryModels.ArtifactRepository;

import java.util.List;

/**
 * Repositories and artifacts. Live client: phase 3 (/api/repositories, /api/storage, AQL).
 */
public interface ArtifactoryClient extends IntegrationHealth {

    List<ArtifactRepository> repositories();

    List<ArtifactInfo> artifacts(String repositoryKey);
}
