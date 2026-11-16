package com.euronet.eft.portal.integration.jenkins;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Caches build lists per job for a minute, so the pipeline table and dashboard don't hammer Jenkins.
 */
@Component
public class JenkinsBuildCache {

    private final JenkinsClient client;
    private final int maxBuilds;

    public JenkinsBuildCache(JenkinsClient client, @Value("${portal.pipelines.max-builds:200}") int maxBuilds) {
        this.client = client;
        this.maxBuilds = maxBuilds;
    }

    @Cacheable(cacheNames = "jenkins-builds", key = "#job")
    public List<JenkinsBuild> builds(String job) {
        return client.builds(job, maxBuilds);
    }
}
