package com.euronet.eft.portal.pipelines;

import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuild;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuildStatsTest {

    static JenkinsBuild build(int number, BuildResult result) {
        return new JenkinsBuild("job", number, result, Instant.now(), Duration.ofMinutes(3), "u", "1.0." + number,
            result == BuildResult.SUCCESS, null);
    }

    @Test
    void countsEachResult() {
        BuildStats s = BuildStats.of(List.of(
            build(1, BuildResult.SUCCESS), build(2, BuildResult.SUCCESS), build(3, BuildResult.SUCCESS),
            build(4, BuildResult.FAILURE), build(5, BuildResult.ABORTED), build(6, BuildResult.UNSTABLE),
            build(7, BuildResult.IN_PROGRESS)));

        assertThat(s).isEqualTo(new BuildStats(7, 3, 1, 1, 1, 1));
        assertThat(s.successRate()).isEqualTo(50.0); // 3 of 6 finished
    }

    @Test
    void successRateIsNullWithoutFinishedBuilds() {
        assertThat(BuildStats.EMPTY.successRate()).isNull();
        assertThat(BuildStats.of(List.of(build(1, BuildResult.IN_PROGRESS))).successRate()).isNull();
    }

    @Test
    void roundsToOneDecimal() {
        BuildStats s = new BuildStats(3, 2, 1, 0, 0, 0);
        assertThat(s.successRate()).isEqualTo(66.7);
    }

    @Test
    void addsUp() {
        assertThat(new BuildStats(2, 1, 1, 0, 0, 0).plus(new BuildStats(3, 1, 0, 1, 0, 1)))
            .isEqualTo(new BuildStats(5, 2, 1, 1, 0, 1));
    }
}
