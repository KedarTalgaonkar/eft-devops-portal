package com.euronet.eft.portal.ai.builds;

import com.euronet.eft.portal.ai.builds.BuildDiagnosis.Category;
import com.euronet.eft.portal.ai.builds.BuildDiagnosis.Confidence;
import com.euronet.eft.portal.ai.builds.BuildDiagnosis.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mock mode: a keyword-rule diagnosis of the (already masked) log, so the explainer works end to end without any
 * external call. Not intelligent: it recognises the common Maven / Jenkins / Tomcat failure signatures only.
 */
final class MockBuildDiagnoser {

    private static final List<Rule> RULES = List.of(
        new Rule(Category.COMPILE, Owner.DEVELOPER, false,
            Pattern.compile("(?i)COMPILATION ERROR|cannot find symbol|incompatible types|Compilation failure"),
            "The code does not compile.",
            "The Java compiler reported errors, so no artifact was built. A recent commit most likely uses a method or type that does not exist on this branch, or a shared library version changed.",
            List.of("Open the first [ERROR] line: it names the file and line.",
                "Check whether the referenced method/class exists in the library version this branch uses.",
                "Build locally with the same profile (mvn -B clean package) before pushing again.")),
        new Rule(Category.TEST, Owner.DEVELOPER, false,
            Pattern.compile("(?i)There are test failures|Tests run: \\d+, Failures: [1-9]|<<< FAILURE!|AssertionFailedError"),
            "Unit tests fail.",
            "The build compiled but one or more tests failed. The failing test and assertion are listed in the evidence; date, time-zone or test-data assumptions are common causes.",
            List.of("Run the failing test class locally.",
                "Compare expected and actual values in the assertion message.",
                "If the test depends on the date or time zone, make the clock injectable or fix the zone (Asia/Kolkata).")),
        new Rule(Category.DEPENDENCY, Owner.DEVOPS, true,
            Pattern.compile("(?i)Could not resolve dependencies|Could not transfer artifact|Could not find artifact"),
            "A library could not be downloaded from Artifactory.",
            "Maven could not download a dependency. A 401/403 means the build's repository credentials are wrong or expired; a 404 means the artifact version was never published.",
            List.of("Check the HTTP status in the error: 401/403 = credentials, 404 = missing version.",
                "Renew the Artifactory credentials used by Jenkins (credentials store, not settings.xml in the repo).",
                "Confirm the dependency version exists in the release repository.")),
        new Rule(Category.QUALITY_GATE, Owner.DEVELOPER, false,
            Pattern.compile("(?i)quality gate (is )?'?ERROR|quality gate failure"),
            "The SonarQube quality gate failed.",
            "Build and tests passed, but the change does not meet the quality gate (for example coverage on new code or unreviewed security hotspots).",
            List.of("Open the SonarQube project and look at the failed conditions on new code.",
                "Add tests for the changed code or review the security hotspots.",
                "Re-run the pipeline after pushing the fix.")),
        new Rule(Category.DEPLOYMENT, Owner.MIDDLEWARE, true,
            Pattern.compile("(?i)Connection refused|Failed to connect|manager/text.*(FAIL|401|403)|Deployment to .* failed"),
            "Deployment to Tomcat failed: the server did not accept the connection.",
            "The artifact was built, but the Tomcat manager on the target host could not be reached (connection refused). Tomcat is probably down, on another port, or blocked by a firewall.",
            List.of("Check that Tomcat is running on the target host and listening on the manager port.",
                "Check firewall rules between the Jenkins agent and the host.",
                "Retry the deploy stage once the server is up; no code change is needed.")),
        new Rule(Category.TIMEOUT, Owner.QA, true,
            Pattern.compile("(?i)timed out|Timeout has been exceeded"),
            "The build was stopped by the pipeline timeout.",
            "A stage waited too long (here: integration tests waiting for a dependent system) and Jenkins aborted the build. The system under test or a simulator was probably not responding.",
            List.of("Check whether the dependent system or simulator was up at the time.",
                "Look for a hanging test in the stage that timed out.",
                "Only raise the timeout if runs normally take close to the limit.")),
        new Rule(Category.INFRASTRUCTURE, Owner.DEVOPS, true,
            Pattern.compile("(?i)No space left on device|OutOfMemoryError|agent.*offline|Cannot allocate memory"),
            "The build agent ran out of resources.",
            "The failure comes from the Jenkins agent, not the code: the disk (or memory) on the agent was full.",
            List.of("Free disk space on the agent (old workspaces, Docker images, Maven cache).",
                "Add a workspace clean-up step or discard old builds.",
                "Re-run the build; no code change is needed.")));

    private MockBuildDiagnoser() {
    }

    static BuildDiagnosis diagnose(String maskedLog) {
        List<String> lines = Arrays.asList(maskedLog.split("\n"));
        for (Rule rule : RULES) {
            List<String> evidence = lines.stream().filter(l -> rule.signal().matcher(l).find()).map(String::strip).distinct().limit(4).toList();
            if (!evidence.isEmpty()) {
                return new BuildDiagnosis(rule.category(), rule.summary(), rule.cause(), evidence, rule.fix(), rule.owner(),
                    Confidence.HIGH, rule.notCode());
            }
        }
        List<String> errors = lines.stream().filter(l -> l.toLowerCase(Locale.ROOT).contains("error")).map(String::strip).limit(4).toList();
        return new BuildDiagnosis(Category.UNKNOWN, "The cause is not clear from the log.",
            "No known failure signature was found. The error lines below are the best starting point.",
            errors, List.of("Open the full console log in Jenkins and look at the first error.",
            "Compare with the last successful build of this job."),
            Owner.DEVOPS, Confidence.LOW, false);
    }

    private record Rule(Category category, Owner owner, boolean notCode, Pattern signal, String summary, String cause,
                        List<String> fix) {
    }
}
