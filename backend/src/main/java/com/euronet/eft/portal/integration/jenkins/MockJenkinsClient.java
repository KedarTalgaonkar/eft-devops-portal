package com.euronet.eft.portal.integration.jenkins;

import com.euronet.eft.portal.integration.IntegrationStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Generates believable build history for any job name. The same job always gets the same history
 * (seeded by its name), and the history moves forward hour by hour, so the numbers look alive but stable.
 */
@Component
@ConditionalOnProperty(prefix = "portal.integrations.jenkins", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockJenkinsClient implements JenkinsClient {

    private static final String[] USERS = {"jenkins-scm", "narsing", "shweta", "deepak", "ganesh", "samar", "timer"};

    @Override
    public List<JenkinsBuild> builds(String job, int limit) {
        Random random = new Random(job.hashCode());
        Instant anchor = Instant.now().truncatedTo(ChronoUnit.HOURS);
        int count = 12 + random.nextInt(50) + (job.toUpperCase().contains("DEVELOP") ? 40 : 0);
        int firstNumber = 1 + random.nextInt(400);

        List<Instant> starts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long minutesAgo = (long) (random.nextDouble() * 60 * 24 * 60); // last 60 days
            starts.add(anchor.minus(minutesAgo, ChronoUnit.MINUTES));
        }
        starts.sort(Comparator.naturalOrder());

        List<JenkinsBuild> builds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int number = firstNumber + i;
            boolean newest = i == count - 1;
            BuildResult result = newest && random.nextInt(10) == 0 ? BuildResult.IN_PROGRESS : pick(random);
            Duration duration = Duration.ofSeconds(90 + random.nextInt(18 * 60));
            String version = "1." + (number / 10) + "." + (number % 10);
            builds.add(new JenkinsBuild(job, number, result, starts.get(i), duration,
                    USERS[random.nextInt(USERS.length)], version, result == BuildResult.SUCCESS,
                    "https://jenkins.example.internal/job/" + job + "/" + number + "/"));
        }
        return builds.reversed().stream().limit(limit).toList();
    }

    private static BuildResult pick(Random random) {
        double p = random.nextDouble();
        if (p < 0.72) {
            return BuildResult.SUCCESS;
        }
        if (p < 0.86) {
            return BuildResult.FAILURE;
        }
        if (p < 0.95) {
            return BuildResult.ABORTED;
        }
        return BuildResult.UNSTABLE;
    }

    @Override
    public IntegrationStatus status() {
        return IntegrationStatus.mock("jenkins", "Jenkins");
    }
}
