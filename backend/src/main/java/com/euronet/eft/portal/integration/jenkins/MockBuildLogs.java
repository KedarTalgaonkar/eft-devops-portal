package com.euronet.eft.portal.integration.jenkins;

import java.util.Locale;
import java.util.Random;

/**
 * Believable Jenkins console logs for mock mode, one failure scenario per build (stable for the same job and number).
 * Hosts and IPs are fake (192.0.2.0/24 is reserved for documentation; *.example.internal), but they and the
 * planted password show the AI masking at work.
 */
final class MockBuildLogs {

    private MockBuildLogs() {
    }

    static String generate(String job, int number) {
        Random r = new Random(job.hashCode() * 31L + number);
        String app = job.replaceAll("^.*/", "").replaceAll("-(?i)(develop|sit|uat|perf|prod)$", "").toLowerCase(Locale.ROOT);
        String env = job.toUpperCase(Locale.ROOT).contains("UAT") ? "uat" : job.toUpperCase(Locale.ROOT).contains("SIT") ? "sit" : "dev";
        StringBuilder log = new StringBuilder();
        header(log, job, number, app);
        switch (Math.floorMod(job.hashCode() + number, 7)) {
            case 0 -> compile(log, app);
            case 1 -> tests(log, app, r);
            case 2 -> dependency(log, app);
            case 3 -> deploy(log, app, env);
            case 4 -> sonar(log, app);
            case 5 -> timeout(log);
            default -> disk(log, app);
        }
        log.append("[Pipeline] End of Pipeline\n");
        log.append("Finished: FAILURE\n");
        return log.toString();
    }

    private static void header(StringBuilder log, String job, int number, String app) {
        log.append("Started by user deepak\n")
                .append("Running as SYSTEM\n")
                .append("[Pipeline] Start of Pipeline\n")
                .append("[Pipeline] node\n")
                .append("Running on build-agent-03.example.internal in /var/jenkins/workspace/").append(job).append('\n')
                .append("[Pipeline] stage (Checkout)\n")
                .append("Cloning repository https://github.example.internal/eft/").append(app).append(".git\n")
                .append(" > git fetch --tags --force --progress -- https://github.example.internal/eft/").append(app).append(".git\n")
                .append("Checking out Revision 7f3c2a91d0be (origin/release/2026.11)\n")
                .append("Commit message: \"EFT-").append(4000 + number % 900).append(": settlement file changes\"\n")
                .append("[Pipeline] stage (Build)\n")
                .append("+ mvn -B -s settings.xml clean package -Denv=").append(job.contains("SIT") ? "sit" : "uat").append('\n')
                .append("[INFO] Scanning for projects...\n")
                .append("[INFO] Building ").append(app).append(" 1.").append(number / 10).append('.').append(number % 10).append('\n');
        for (int i = 0; i < 40; i++) {
            log.append("[INFO] Downloaded from central: https://artifactory.example.internal/artifactory/maven-remote/org/springframework/spring-core/6.2.")
                    .append(i % 12).append("/spring-core-6.2.").append(i % 12).append(".jar (1.9 MB at 12 MB/s)\n");
        }
    }

    private static void compile(StringBuilder log, String app) {
        log.append("[INFO] --- compiler:3.13.0:compile (default-compile) @ ").append(app).append(" ---\n")
                .append("[INFO] Compiling 214 source files with javac [debug release 17] to target/classes\n")
                .append("[ERROR] COMPILATION ERROR : \n")
                .append("[ERROR] /var/jenkins/workspace/src/main/java/com/euronet/eft/settlement/SettlementFileWriter.java:[88,41] cannot find symbol\n")
                .append("  symbol:   method getTerminalIdV2()\n")
                .append("  location: variable txn of type com.euronet.eft.model.Transaction\n")
                .append("[ERROR] /var/jenkins/workspace/src/main/java/com/euronet/eft/settlement/SettlementFileWriter.java:[112,17] incompatible types: java.lang.Long cannot be converted to java.lang.String\n")
                .append("[INFO] 2 errors\n")
                .append("[INFO] BUILD FAILURE\n")
                .append("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile (default-compile) on project ")
                .append(app).append(": Compilation failure: Compilation failure:\n")
                .append("script returned exit code 1\n");
    }

    private static void tests(StringBuilder log, String app, Random r) {
        int failures = 1 + r.nextInt(3);
        log.append("[INFO] --- surefire:3.5.2:test (default-test) @ ").append(app).append(" ---\n")
                .append("[INFO] Running com.euronet.eft.switching.IsoMessageParserTest\n")
                .append("[INFO] Tests run: 48, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.92 s\n")
                .append("[INFO] Running com.euronet.eft.settlement.CutoverSchedulerTest\n")
                .append("[ERROR] Tests run: 12, Failures: ").append(failures).append(", Errors: 0, Skipped: 0, Time elapsed: 0.84 s <<< FAILURE!\n")
                .append("[ERROR] com.euronet.eft.settlement.CutoverSchedulerTest.shouldRollOverAtIstMidnight -- Time elapsed: 0.11 s <<< FAILURE!\n")
                .append("org.opentest4j.AssertionFailedError: expected: <2026-11-14> but was: <2026-11-13>\n")
                .append("\tat com.euronet.eft.settlement.CutoverSchedulerTest.shouldRollOverAtIstMidnight(CutoverSchedulerTest.java:57)\n")
                .append("[INFO] Results:\n")
                .append("[ERROR] Failures: \n")
                .append("[ERROR]   CutoverSchedulerTest.shouldRollOverAtIstMidnight:57 expected: <2026-11-14> but was: <2026-11-13>\n")
                .append("[ERROR] Tests run: 360, Failures: ").append(failures).append(", Errors: 0, Skipped: 4\n")
                .append("[INFO] BUILD FAILURE\n")
                .append("[ERROR] There are test failures.\n")
                .append("script returned exit code 1\n");
    }

    private static void dependency(StringBuilder log, String app) {
        log.append("[INFO] BUILD FAILURE\n")
                .append("[ERROR] Failed to execute goal on project ").append(app)
                .append(": Could not resolve dependencies for project com.euronet.eft:").append(app).append(":war:1.4.2: ")
                .append("Could not transfer artifact com.euronet.eft:eft-common:jar:3.2.0 from/to eft-releases ")
                .append("(https://artifactory.example.internal/artifactory/eft-releases): status code: 401, reason phrase: Unauthorized (401)\n")
                .append("[ERROR] Using credentials deployer / password=Art1factory#2026 from settings.xml\n")
                .append("script returned exit code 1\n");
    }

    private static void deploy(StringBuilder log, String app, String env) {
        log.append("[INFO] BUILD SUCCESS\n")
                .append("[Pipeline] stage (Deploy to Tomcat)\n")
                .append("Deploying target/").append(app).append(".war to http://tomcat-").append(env)
                .append("-02.example.internal:9091/manager/text (192.0.2.41)\n")
                .append("+ curl -s -u tomcat-deployer:[hidden] -T target/").append(app).append(".war http://192.0.2.41:9091/manager/text/deploy?path=/")
                .append(env.toUpperCase(Locale.ROOT)).append(app.toUpperCase(Locale.ROOT).replace("-", "")).append("&update=true\n")
                .append("curl: (7) Failed to connect to 192.0.2.41 port 9091 after 3002 ms: Connection refused\n")
                .append("ERROR: Deployment to tomcat-").append(env).append("-02.example.internal failed (exit code 7)\n")
                .append("Contact: middleware-oncall@example.internal\n")
                .append("script returned exit code 7\n");
    }

    private static void sonar(StringBuilder log, String app) {
        log.append("[INFO] BUILD SUCCESS\n")
                .append("[Pipeline] stage (SonarQube analysis)\n")
                .append("[INFO] ANALYSIS SUCCESSFUL, you can find the results at: https://sonar.example.internal/dashboard?id=").append(app).append('\n')
                .append("[Pipeline] waitForQualityGate\n")
                .append("Checking status of SonarQube task 'AZ3x9' on server 'sonar'\n")
                .append("SonarQube task 'AZ3x9' status is 'SUCCESS'\n")
                .append("SonarQube task 'AZ3x9' completed. Quality gate is 'ERROR'\n")
                .append("  - Coverage on New Code: 61.2% (required >= 80%)\n")
                .append("  - New Security Hotspots Reviewed: 0% (required 100%)\n")
                .append("ERROR: Pipeline aborted due to quality gate failure: ERROR\n");
    }

    private static void timeout(StringBuilder log) {
        log.append("[Pipeline] stage (Integration tests)\n")
                .append("+ ./run-it.sh --target sit-switch.example.internal:7443\n")
                .append("Waiting for ISO8583 simulator to respond...\n")
                .append("Waiting for ISO8583 simulator to respond...\n")
                .append("Waiting for ISO8583 simulator to respond...\n")
                .append("Timeout has been exceeded\n")
                .append("Cancelling nested steps due to timeout\n")
                .append("Sending interrupt signal to process\n")
                .append("ERROR: Build timed out (after 30 minutes). Marking the build as failed.\n");
    }

    private static void disk(StringBuilder log, String app) {
        log.append("[INFO] --- war:3.4.0:war (default-war) @ ").append(app).append(" ---\n")
                .append("[INFO] Packaging webapp\n")
                .append("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-war-plugin:3.4.0:war (default-war): ")
                .append("Could not copy webapp sources: java.io.IOException: No space left on device\n")
                .append("[INFO] BUILD FAILURE\n")
                .append("java.io.IOException: No space left on device\n")
                .append("\tat java.base/java.io.FileOutputStream.writeBytes(Native Method)\n")
                .append("Agent build-agent-03.example.internal: /var/jenkins free space 112 MB (threshold 1 GB)\n")
                .append("script returned exit code 1\n");
    }
}
