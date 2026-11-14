package com.euronet.eft.portal.ai.builds;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What the AI concludes about a failed build. The JSON schema of this record constrains the model's answer.
 */
public record BuildDiagnosis(
        @JsonPropertyDescription("Root-cause category of the failure")
        Category category,
        @JsonPropertyDescription("One sentence a manager understands, e.g. 'Unit test CutoverSchedulerTest fails on the IST date roll-over'")
        String summary,
        @JsonPropertyDescription("The most likely cause, two to four sentences, naming the file, test, host or step involved")
        String likelyCause,
        @JsonPropertyDescription("Up to 5 log lines copied exactly from the log that prove the cause")
        List<String> evidence,
        @JsonPropertyDescription("Concrete steps to fix it, most likely first, at most 5")
        List<String> fixSteps,
        @JsonPropertyDescription("Who should act: DEVELOPER (code/tests), DEVOPS (pipeline, agents, credentials), MIDDLEWARE (Tomcat, servers, network), or QA")
        Owner owner,
        @JsonPropertyDescription("HIGH when the log clearly shows the cause, MEDIUM when it is probable, LOW when the log is inconclusive")
        Confidence confidence,
        @JsonPropertyDescription("True when the code is probably fine and the failure is caused by infrastructure, credentials or the environment")
        boolean notACodeProblem) {

    public enum Category {
        COMPILE, TEST, DEPENDENCY, QUALITY_GATE, DEPLOYMENT, INFRASTRUCTURE, TIMEOUT, CONFIGURATION, UNKNOWN
    }

    public enum Owner { DEVELOPER, DEVOPS, MIDDLEWARE, QA }

    public enum Confidence { HIGH, MEDIUM, LOW }
}
