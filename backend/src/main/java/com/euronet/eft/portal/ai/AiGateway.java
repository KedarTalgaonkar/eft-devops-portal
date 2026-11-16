package com.euronet.eft.portal.ai;

import com.euronet.eft.portal.ai.usage.AiUsageService;
import com.euronet.eft.portal.inventory.Deployment;
import com.euronet.eft.portal.inventory.InventoryService;
import com.euronet.eft.portal.security.CurrentUser;
import com.euronet.eft.portal.security.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The only way the portal talks to an AI model. For every request it:
 * <ol>
 *   <li>refuses when AI is off, the user's daily limit or the monthly budget is used up;</li>
 *   <li>masks secrets, IPs, internal hosts and e-mails ({@link Redactor});</li>
 *   <li>calls Claude (LIVE) or the feature's built-in sample answer (MOCK);</li>
 *   <li>restores masked hosts/IPs in the answer for display;</li>
 *   <li>records tokens, estimated cost and outcome in the usage ledger and the audit trail.</li>
 * </ol>
 */
@Service
public class AiGateway {

    static final String SYSTEM_USER = "system";
    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    private final AiProperties props;
    private final ClaudeModel claude;
    private final AiUsageService usage;
    private final AuditService audit;
    private final InventoryService inventory;
    private final JsonMapper json;

    public AiGateway(AiProperties props, ClaudeModel claude, AiUsageService usage, AuditService audit,
                     InventoryService inventory, JsonMapper json) {
        this.props = props;
        this.claude = claude;
        this.usage = usage;
        this.audit = audit;
        this.inventory = inventory;
        this.json = json;
    }

    private static String host(String url) {
        try {
            String u = url.contains("://") ? url : "http://" + url;
            return URI.create(u.strip()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Runs a task for the signed-in user (counts towards their daily limit).
     */
    public <T> AiOutcome<T> run(AiTask<T> task) {
        CurrentUser user = CurrentUser.current();
        return run(task, user == null ? SYSTEM_USER : user.username(), user == null ? "SYSTEM" : user.source().name(),
            user == null);
    }

    /**
     * Runs a task for a scheduled job (exempt from per-user limits, still within the monthly budget).
     */
    public <T> AiOutcome<T> runAsSystem(AiTask<T> task) {
        return run(task, SYSTEM_USER, "SYSTEM", true);
    }

    private <T> AiOutcome<T> run(AiTask<T> task, String username, String source, boolean system) {
        if (!props.enabled()) {
            throw AiException.disabled();
        }
        if (!system && usage.callsToday(username) >= props.userDailyLimit()) {
            throw new AiException(HttpStatus.TOO_MANY_REQUESTS,
                "You have reached today's limit of " + props.userDailyLimit() + " AI requests. Try again tomorrow.");
        }
        boolean live = props.mode() == AiProperties.Mode.LIVE;
        if (live && usage.spentThisMonth().doubleValue() >= props.monthlyBudgetUsd()) {
            throw new AiException(HttpStatus.TOO_MANY_REQUESTS,
                "This month's AI budget has been used. An administrator can raise AI_MONTHLY_BUDGET_USD.");
        }

        Redactor.Redaction masked = redactor().redact(task.input());
        String mode = props.mode().name();
        long started = System.nanoTime();
        try {
            T value;
            String model;
            long in = 0;
            long out = 0;
            long cached = 0;
            if (live) {
                ClaudeModel.Reply<T> reply = claude.structured(task.system(), masked.text(), task.type(), task.effort(), task.maxTokens());
                value = reply.value();
                model = reply.model();
                in = reply.inputTokens();
                out = reply.outputTokens();
                cached = reply.cacheReadTokens();
            } else {
                value = task.mock().apply(masked.text());
                model = "sample";
            }
            T restored = restore(value, masked, task.type());
            long ms = (System.nanoTime() - started) / 1_000_000;
            usage.record(task.feature(), task.subject(), username, mode, model, in, out, cached, ms, "SUCCESS", masked.summary());
            audit.record(username, source, "AI_REQUEST", task.feature() + " " + task.subject(), "SUCCESS",
                mode.toLowerCase() + " · " + model + " · " + in + " in / " + out + " out tokens · " + masked.summary());
            return new AiOutcome<>(restored, mode, model, masked.summary());
        } catch (AiException e) {
            fail(task, username, source, mode, started, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("AI request {} {} failed", task.feature(), task.subject(), e);
            fail(task, username, source, mode, started, e.getClass().getSimpleName());
            throw new AiException(HttpStatus.BAD_GATEWAY, "The AI request failed. Try again later.", e);
        }
    }

    private void fail(AiTask<?> task, String username, String source, String mode, long started, String reason) {
        long ms = (System.nanoTime() - started) / 1_000_000;
        usage.record(task.feature(), task.subject(), username, mode, props.model(), 0, 0, 0, ms, "FAILURE", reason);
        audit.record(username, source, "AI_REQUEST", task.feature() + " " + task.subject(), "FAILURE", reason);
    }

    /**
     * Puts real hosts / IPs / e-mails back by round-tripping the answer through JSON.
     */
    private <T> T restore(T value, Redactor.Redaction masked, Class<T> type) {
        if (masked.restore().isEmpty() || value == null) {
            return value;
        }
        return json.readValue(masked.restore(json.writeValueAsString(value)), type);
    }

    /**
     * Masks the configured internal domains plus every host from the inventory (Tomcat base URLs).
     */
    Redactor redactor() {
        Set<String> hosts = inventory.allDeployments().stream()
            .map(Deployment::getBaseUrl)
            .filter(Objects::nonNull)
            .map(AiGateway::host)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return new Redactor(props.maskDomains(), hosts);
    }

    // ---------- status for the UI ----------

    public AiStatus status() {
        boolean live = props.mode() == AiProperties.Mode.LIVE;
        String label = switch (props.mode()) {
            case OFF -> "AI features are off";
            case MOCK -> "Sample AI answers (mock mode, nothing is sent outside the portal)";
            case LIVE -> "Claude " + props.model() + " via " + switch (props.provider()) {
                case ANTHROPIC -> "Anthropic API";
                case BEDROCK -> "Amazon Bedrock";
                case FOUNDRY -> "Microsoft Foundry";
            };
        };
        return new AiStatus(props.enabled(), props.mode().name(), props.provider().name(), props.model(),
            !live || claude.configured(), props.digest().enabled(), props.userDailyLimit(), label);
    }

    public record AiStatus(boolean enabled, String mode, String provider, String model, boolean configured,
                           boolean digestEnabled, int userDailyLimit, String label) {
    }
}
