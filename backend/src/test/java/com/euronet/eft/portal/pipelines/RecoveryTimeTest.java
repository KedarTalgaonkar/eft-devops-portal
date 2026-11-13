package com.euronet.eft.portal.pipelines;

import static org.assertj.core.api.Assertions.assertThat;

import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuild;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryTimeTest {

    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    private static JenkinsBuild at(int hours, BuildResult result) {
        return new JenkinsBuild("job", hours, result, T0.plus(Duration.ofHours(hours)), Duration.ofMinutes(5), "u", null,
                result == BuildResult.SUCCESS, null);
    }

    @Test
    void measuresFromFirstFailureOfAStreakToTheNextSuccess() {
        // newest first, as Jenkins returns them
        List<JenkinsBuild> builds = List.of(
                at(20, BuildResult.FAILURE),   // open streak: not counted
                at(12, BuildResult.SUCCESS),
                at(9, BuildResult.FAILURE),
                at(6, BuildResult.FAILURE),    // streak starts here -> 6h to the success at 12
                at(5, BuildResult.SUCCESS),
                at(4, BuildResult.ABORTED),    // aborted neither starts nor ends a streak
                at(2, BuildResult.UNSTABLE),   // streak 2 -> 5 = 3h
                at(0, BuildResult.SUCCESS));

        List<Double> hours = new ArrayList<>();
        PipelineInsightsService.recoveryHours(builds, T0, T0.plus(Duration.ofDays(1)), hours);

        assertThat(hours).containsExactly(3.0, 6.0);
    }

    @Test
    void ignoresStreaksStartingOutsideTheWindow() {
        List<JenkinsBuild> builds = List.of(at(12, BuildResult.SUCCESS), at(6, BuildResult.FAILURE));
        List<Double> hours = new ArrayList<>();
        PipelineInsightsService.recoveryHours(builds, T0.plus(Duration.ofHours(7)), T0.plus(Duration.ofDays(1)), hours);
        assertThat(hours).isEmpty();
    }
}
