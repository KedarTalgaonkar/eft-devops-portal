package com.euronet.eft.portal.ai.builds;

import com.euronet.eft.portal.ai.AiException;
import com.euronet.eft.portal.ai.AiGateway;
import com.euronet.eft.portal.ai.AiOutcome;
import com.euronet.eft.portal.ai.AiProperties;
import com.euronet.eft.portal.ai.AiTask;
import com.euronet.eft.portal.ai.insight.AiInsight;
import com.euronet.eft.portal.ai.insight.AiInsightService;
import com.euronet.eft.portal.integration.jenkins.BuildLog;
import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.integration.jenkins.JenkinsClient;
import com.euronet.eft.portal.pipelines.PipelineDtos.BuildView;
import com.euronet.eft.portal.pipelines.PipelineService;
import com.euronet.eft.portal.pipelines.PipelineService.PipelineRef;
import com.euronet.eft.portal.security.CurrentUser;
import com.euronet.eft.portal.security.PortalRole;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Explains why a Jenkins build failed: fetches the log, sends the masked tail to the model, stores the answer so
 * the next person who opens the same build sees it instantly (and nothing is paid twice).
 */
@Service
public class BuildExplanationService {

    public static final String FEATURE = "build-diagnosis";

    private static final Set<BuildResult> EXPLAINABLE = EnumSet.of(BuildResult.FAILURE, BuildResult.UNSTABLE, BuildResult.ABORTED);

    static final String SYSTEM_PROMPT = """
            You are a senior build and release engineer for Euronet's EFT (payments switching) applications, which are
            Java/Maven projects built by Jenkins and deployed to Apache Tomcat servers in DEVELOP, SIT and UAT.

            You receive the end of the console log of one failed Jenkins build, plus error lines from earlier in the log.
            Find the root cause and explain it for the support engineer who has to fix it.

            Rules:
            - Base every statement on the log. Quote evidence lines exactly as they appear, without changing them.
            - The first real error usually matters most; later errors are often consequences of it.
            - Values such as [IP_1], [HOST_2], [EMAIL_1] and [SECRET] are masked on purpose. Refer to them by these
              placeholders; never guess or ask for the real values.
            - Mark notACodeProblem true when the code is probably fine (agent disk full, server down, credentials
              expired, network, timeouts caused by another system).
            - If the log does not show the cause, say so with category UNKNOWN and confidence LOW rather than guessing.
            - Fix steps must be concrete actions for this pipeline, not generic advice. No more than five.
            - Write plain, short English. No markdown.
            """;

    private final AiGateway gateway;
    private final AiInsightService insights;
    private final PipelineService pipelines;
    private final JenkinsClient jenkins;
    private final AiProperties props;

    public BuildExplanationService(AiGateway gateway, AiInsightService insights, PipelineService pipelines,
                                   JenkinsClient jenkins, AiProperties props) {
        this.gateway = gateway;
        this.insights = insights;
        this.pipelines = pipelines;
        this.jenkins = jenkins;
        this.props = props;
    }

    /** A stored explanation plus context for the UI. */
    public record BuildExplanation(AiInsightService.Stored<BuildDiagnosis> result, String job, int buildNumber,
                                   long logBytes, boolean logTruncated, List<SimilarFailure> similar) {
    }

    /** An earlier explained build of the same job with the same category: a repeating problem. */
    public record SimilarFailure(int buildNumber, String summary, java.time.Instant explainedAt) {
    }

    /** The stored explanation, if this build was explained before. */
    public Optional<BuildExplanation> find(long applicationId, String environment, int number) {
        PipelineRef ref = pipelines.ref(applicationId, environment);
        return insights.find(FEATURE, subject(ref, number), BuildDiagnosis.class, user())
                .map(r -> new BuildExplanation(r, ref.job(), number, 0, false, similar(ref, number, r.content())));
    }

    /**
     * Explains the build; returns the stored explanation unless {@code refresh} is set (editors and administrators
     * only, since it costs a new call).
     */
    public BuildExplanation explain(long applicationId, String environment, int number, boolean refresh) {
        if (!props.enabled()) {
            throw AiException.disabled();
        }
        PipelineRef ref = pipelines.ref(applicationId, environment);
        String subject = subject(ref, number);
        if (!refresh) {
            Optional<BuildExplanation> existing = find(applicationId, environment, number);
            if (existing.isPresent()) {
                return existing.get();
            }
        } else if (!canRegenerate()) {
            throw new AiException(HttpStatus.FORBIDDEN, "Only editors and administrators can ask for a new explanation.");
        }
        BuildView build = pipelines.build(applicationId, environment, number);
        if (!EXPLAINABLE.contains(build.result())) {
            throw new AiException(HttpStatus.BAD_REQUEST, "Only failed, unstable or aborted builds can be explained.");
        }
        BuildLog log = jenkins.log(ref.job(), number, props.maxLogKb() * 1024);
        String input = prompt(ref, build, log);
        AiOutcome<BuildDiagnosis> outcome = gateway.run(new AiTask<>(FEATURE, subject, BuildDiagnosis.class, SYSTEM_PROMPT,
                input, AiTask.Effort.MEDIUM, 8000, MockBuildDiagnoser::diagnose));
        AiInsightService.Stored<BuildDiagnosis> stored = insights.save(FEATURE, subject, outcome, user(), BuildDiagnosis.class);
        return new BuildExplanation(stored, ref.job(), number, log.totalBytes(), log.truncated(),
                similar(ref, number, stored.content()));
    }

    static String prompt(PipelineRef ref, BuildView build, BuildLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pipeline: ").append(ref.project()).append(" (client ").append(ref.client()).append(") in ")
                .append(ref.environment()).append('\n')
                .append("Jenkins job: ").append(ref.job()).append(" build #").append(build.number())
                .append(", result ").append(build.result()).append(", duration ").append(build.durationSeconds()).append(" s\n")
                .append("Log size: ").append(log.totalBytes()).append(" bytes")
                .append(log.truncated() ? " (only the end is included)" : "").append("\n\n");
        if (!log.earlyErrors().isEmpty()) {
            sb.append("<earlier_error_lines>\n").append(String.join("\n", log.earlyErrors())).append("\n</earlier_error_lines>\n\n");
        }
        sb.append("<log_tail>\n").append(log.tail()).append("\n</log_tail>\n\n")
                .append("Diagnose this failure.");
        return sb.toString();
    }

    private List<SimilarFailure> similar(PipelineRef ref, int number, BuildDiagnosis current) {
        if (current == null || current.category() == BuildDiagnosis.Category.UNKNOWN) {
            return List.of();
        }
        return insights.withSubjectPrefix(FEATURE, ref.job() + "#", 30).stream()
                .filter(i -> !i.getSubject().equals(subject(ref, number)))
                .map(i -> similarOf(i, current.category()))
                .filter(Objects::nonNull)
                .limit(5)
                .toList();
    }

    private SimilarFailure similarOf(AiInsight i, BuildDiagnosis.Category category) {
        try {
            BuildDiagnosis d = insights.read(i, BuildDiagnosis.class);
            if (d.category() != category) {
                return null;
            }
            int n = Integer.parseInt(i.getSubject().substring(i.getSubject().lastIndexOf('#') + 1));
            return new SimilarFailure(n, d.summary(), i.getCreatedAt());
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String subject(PipelineRef ref, int number) {
        return ref.job() + "#" + number;
    }

    private static String user() {
        return CurrentUser.nameOrSystem();
    }

    static boolean canRegenerate() {
        CurrentUser u = CurrentUser.current();
        return u != null && (u.roles().contains(PortalRole.EDITOR) || u.roles().contains(PortalRole.ADMIN));
    }
}
