package com.euronet.eft.portal.ai.digest;

import com.euronet.eft.portal.ai.AiException;
import com.euronet.eft.portal.ai.AiGateway;
import com.euronet.eft.portal.ai.AiOutcome;
import com.euronet.eft.portal.ai.AiProperties;
import com.euronet.eft.portal.ai.AiTask;
import com.euronet.eft.portal.ai.insight.AiInsightService;
import com.euronet.eft.portal.ai.insight.AiInsightService.Stored;
import com.euronet.eft.portal.dashboard.AttentionService;
import com.euronet.eft.portal.dashboard.DashboardDtos.AttentionReport;
import com.euronet.eft.portal.dashboard.DashboardService;
import com.euronet.eft.portal.pipelines.InsightDtos.PipelineInsights;
import com.euronet.eft.portal.pipelines.PipelineInsightsService;
import com.euronet.eft.portal.security.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the executive digest from the portal's own aggregated figures (the same ones the Command centre shows),
 * on a schedule and on demand. Each digest is kept, so leadership can look back.
 */
@Service
public class DigestService {

    public static final String FEATURE = "executive-digest";
    private static final Logger log = LoggerFactory.getLogger(DigestService.class);
    private static final int WINDOW_DAYS = 30;

    static final String SYSTEM_PROMPT = """
            You write the executive digest for the EFT DevOps Portal of Euronet India: a short status report on the
            delivery and operation of the EFT (payments switching) applications for senior management.

            You receive aggregated figures as JSON: overall and per-area RAG status, delivery KPIs for the current
            period with the previous period of the same length for comparison, the estate (applications, services up and
            down, alerts, quality gates) and the top open issues with owners.

            Rules:
            - Use only the figures given. Never invent numbers, names, causes or dates.
            - Lead with what matters most to the business: failing pipelines in production-like environments, services
              down, critical vulnerabilities. Compare with the previous period where a KPI has one.
            - Keep the overall status consistent with the data's overall RAG unless the figures clearly contradict it.
            - Actions must be concrete and assigned to the owner named in the data, or 'Unassigned'.
            - Masked values such as [HOST_1] or [EMAIL_1] are placeholders; keep them as they are.
            - Plain, confident business English. No markdown, no jargon without explanation.
            """;

    private final AiGateway gateway;
    private final AiInsightService insights;
    private final AttentionService attention;
    private final PipelineInsightsService pipelineInsights;
    private final DashboardService dashboard;
    private final AiProperties props;
    private final JsonMapper json;

    public DigestService(AiGateway gateway, AiInsightService insights, AttentionService attention,
                         PipelineInsightsService pipelineInsights, DashboardService dashboard, AiProperties props,
                         JsonMapper json) {
        this.gateway = gateway;
        this.insights = insights;
        this.attention = attention;
        this.pipelineInsights = pipelineInsights;
        this.dashboard = dashboard;
        this.props = props;
        this.json = json;
    }

    public Optional<Stored<ExecutiveDigest>> latest() {
        return history(1).stream().findFirst();
    }

    public List<Stored<ExecutiveDigest>> history(int limit) {
        return insights.latest(FEATURE, ExecutiveDigest.class, limit, CurrentUser.nameOrSystem());
    }

    /** On demand (administrators). */
    public Stored<ExecutiveDigest> generateNow() {
        return generate(false);
    }

    /** Weekday mornings by default (AI_DIGEST_CRON, AI_DIGEST_ZONE). */
    @Scheduled(cron = "${portal.ai.digest.cron:0 0 7 * * MON-FRI}", zone = "${portal.ai.digest.zone:Asia/Kolkata}")
    public void scheduled() {
        if (!props.enabled() || !props.digest().enabled()) {
            return;
        }
        try {
            generate(true);
        } catch (RuntimeException e) {
            log.warn("Scheduled executive digest failed: {}", e.getMessage());
        }
    }

    /** Mock mode: write one sample digest at start-up so the Command centre shows the feature straight away. */
    @EventListener(ApplicationReadyEvent.class)
    public void sampleOnStartup() {
        if (props.mode() == AiProperties.Mode.MOCK && props.digest().enabled() && latest().isEmpty()) {
            try {
                generate(true);
            } catch (RuntimeException e) {
                log.warn("Sample executive digest failed: {}", e.getMessage());
            }
        }
    }

    Stored<ExecutiveDigest> generate(boolean system) {
        if (!props.enabled()) {
            throw AiException.disabled();
        }
        DigestFacts facts = facts();
        String input = "Figures for " + facts.date() + " (window: last " + WINDOW_DAYS + " days vs the " + WINDOW_DAYS
                + " days before):\n" + json.writeValueAsString(facts) + "\n\nWrite the executive digest.";
        String subject = "digest@" + Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AiTask<ExecutiveDigest> task = new AiTask<>(FEATURE, subject, ExecutiveDigest.class, SYSTEM_PROMPT, input,
                AiTask.Effort.MEDIUM, 8000, masked -> MockDigestWriter.write(readFacts(masked)));
        AiOutcome<ExecutiveDigest> outcome = system ? gateway.runAsSystem(task) : gateway.run(task);
        return insights.save(FEATURE, subject, outcome, system ? "system" : CurrentUser.nameOrSystem(), ExecutiveDigest.class);
    }

    DigestFacts facts() {
        AttentionReport report = attention.report();
        PipelineInsights p = pipelineInsights.insights(null, WINDOW_DAYS);
        DigestFacts.Delivery delivery = new DigestFacts.Delivery(p.pipelines(), p.redPipelines(), p.totalBuilds(),
                p.successRate(), p.failureRate(), p.deploymentsPerDay(), p.meanTimeToRecoverHours(),
                p.averageBuildMinutes(), p.environments());
        return new DigestFacts(LocalDate.now(ZoneId.of(props.digest().zone())), WINDOW_DAYS, report.overall(),
                report.areas(), report.items().stream().limit(12).toList(), report.items().size(), delivery,
                DigestFacts.Estate.of(dashboard.overview()));
    }

    /** The mock writer reads the (masked) JSON back, like a model would. */
    private DigestFacts readFacts(String maskedInput) {
        int start = maskedInput.indexOf('{');
        int end = maskedInput.lastIndexOf('}');
        return json.readValue(maskedInput.substring(start, end + 1), DigestFacts.class);
    }
}
