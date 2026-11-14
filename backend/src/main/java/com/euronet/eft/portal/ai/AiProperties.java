package com.euronet.eft.portal.ai;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI settings ({@code portal.ai.*}, from environment variables; see .env.example).
 *
 * @param mode               OFF (default): no AI anywhere; MOCK: built-in sample answers, no external calls; LIVE: Claude
 * @param provider           where LIVE calls go: Anthropic API, Amazon Bedrock or Microsoft Foundry
 * @param model              Claude model id (Bedrock: the "anthropic." prefix is added automatically)
 * @param apiKey             Anthropic API key (provider ANTHROPIC only; Bedrock/Foundry use their own credentials)
 * @param monthlyBudgetUsd   AI calls are refused once the estimated spend this calendar month reaches this
 * @param userDailyLimit     generations per user per day (scheduled jobs are exempt)
 * @param maxLogKb           how much of a build log (its end) is analysed
 * @param maskDomains        host names ending in one of these are masked before sending (plus all inventory hosts)
 * @param pricing            USD per million tokens, for the cost estimate only
 */
@ConfigurationProperties(prefix = "portal.ai")
public record AiProperties(
        Mode mode,
        Provider provider,
        String model,
        String apiKey,
        double monthlyBudgetUsd,
        int userDailyLimit,
        int maxLogKb,
        List<String> maskDomains,
        Pricing pricing,
        Digest digest) {

    public enum Mode { OFF, MOCK, LIVE }

    public enum Provider { ANTHROPIC, BEDROCK, FOUNDRY }

    public record Pricing(double inputPerMtok, double outputPerMtok, double cacheReadPerMtok) {
    }

    /** Executive digest schedule (cron in the given zone). */
    public record Digest(boolean enabled, String cron, String zone) {
    }

    public AiProperties {
        mode = mode == null ? Mode.OFF : mode;
        provider = provider == null ? Provider.ANTHROPIC : provider;
        model = model == null || model.isBlank() ? "claude-opus-5-5" : model.strip();
        monthlyBudgetUsd = monthlyBudgetUsd <= 0 ? 100 : monthlyBudgetUsd;
        userDailyLimit = userDailyLimit <= 0 ? 50 : userDailyLimit;
        maxLogKb = Math.clamp(maxLogKb <= 0 ? 64 : maxLogKb, 8, 256);
        maskDomains = maskDomains == null ? List.of(".internal", ".local", ".corp", ".lan") : List.copyOf(maskDomains);
        pricing = pricing == null ? new Pricing(4.0, 20.0, 0.20) : pricing;
        digest = digest == null ? new Digest(true, "0 0 7 * * MON-FRI", "Asia/Kolkata") : digest;
    }

    public boolean enabled() {
        return mode != Mode.OFF;
    }
}
