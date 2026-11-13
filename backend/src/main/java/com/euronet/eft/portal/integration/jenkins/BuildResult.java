package com.euronet.eft.portal.integration.jenkins;

import java.util.Locale;

/** Jenkins build results, plus IN_PROGRESS for a build that has not finished yet. */
public enum BuildResult {
    SUCCESS, FAILURE, ABORTED, UNSTABLE, NOT_BUILT, IN_PROGRESS;

    public static BuildResult fromJenkins(String result, boolean building) {
        if (building || result == null) {
            return IN_PROGRESS;
        }
        try {
            return valueOf(result.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NOT_BUILT;
        }
    }
}
