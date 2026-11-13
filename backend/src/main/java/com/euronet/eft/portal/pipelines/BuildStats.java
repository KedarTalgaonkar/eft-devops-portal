package com.euronet.eft.portal.pipelines;

import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuild;
import java.util.Collection;

/** Build counts for one job (or many jobs) over a time window. */
public record BuildStats(int total, int success, int failed, int aborted, int unstable, int inProgress) {

    public static final BuildStats EMPTY = new BuildStats(0, 0, 0, 0, 0, 0);

    public static BuildStats of(Collection<JenkinsBuild> builds) {
        int success = 0;
        int failed = 0;
        int aborted = 0;
        int unstable = 0;
        int running = 0;
        for (JenkinsBuild b : builds) {
            switch (b.result()) {
                case SUCCESS -> success++;
                case FAILURE -> failed++;
                case ABORTED -> aborted++;
                case UNSTABLE -> unstable++;
                case IN_PROGRESS -> running++;
                case NOT_BUILT -> { }
            }
        }
        return new BuildStats(builds.size(), success, failed, aborted, unstable, running);
    }

    public BuildStats plus(BuildStats o) {
        return new BuildStats(total + o.total, success + o.success, failed + o.failed,
                aborted + o.aborted, unstable + o.unstable, inProgress + o.inProgress);
    }

    /** Success share of finished builds in percent with one decimal, or null when nothing finished. */
    public Double successRate() {
        int finished = success + failed + aborted + unstable;
        if (finished == 0) {
            return null;
        }
        return Math.round(success * 1000.0 / finished) / 10.0;
    }

    static boolean counts(BuildResult result) {
        return result != BuildResult.NOT_BUILT;
    }
}
