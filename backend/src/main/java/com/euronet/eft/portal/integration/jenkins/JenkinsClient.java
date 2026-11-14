package com.euronet.eft.portal.integration.jenkins;

import com.euronet.eft.portal.integration.IntegrationHealth;
import java.util.List;

/** What the portal needs from Jenkins. Implemented by {@link MockJenkinsClient} and {@link RestJenkinsClient}. */
public interface JenkinsClient extends IntegrationHealth {

    /**
     * Most recent builds of a job, newest first.
     *
     * @param job   full job name; folders separated by "/" (e.g. {@code EFT/prepaid-qr-SIT})
     * @param limit maximum number of builds to return
     * @return an empty list when the job does not exist
     */
    List<JenkinsBuild> builds(String job, int limit);

    /**
     * Console log of one build: the last {@code maxBytes} plus earlier error lines.
     *
     * @throws com.euronet.eft.portal.common.NotFoundException when the job or build does not exist
     */
    BuildLog log(String job, int number, int maxBytes);
}
