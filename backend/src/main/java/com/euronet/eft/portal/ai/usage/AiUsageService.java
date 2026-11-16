package com.euronet.eft.portal.ai.usage;

import com.euronet.eft.portal.ai.AiProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Usage and cost ledger: limits per user and per month, and the administrator's usage view.
 */
@Service
public class AiUsageService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiUsageRepository repository;
    private final AiProperties props;
    private final ZoneId zone;

    public AiUsageService(AiUsageRepository repository, AiProperties props) {
        this.repository = repository;
        this.props = props;
        this.zone = ZoneId.of(props.digest().zone());
    }

    private static List<Totals> totals(List<AiUsage> rows, java.util.function.Function<AiUsage, String> key) {
        Map<String, List<AiUsage>> groups = rows.stream()
            .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
        return groups.entrySet().stream()
            .map(e -> new Totals(e.getKey(), e.getValue().size(),
                e.getValue().stream().filter(u -> !"SUCCESS".equals(u.getOutcome())).count(),
                e.getValue().stream().mapToLong(AiUsage::getInputTokens).sum(),
                e.getValue().stream().mapToLong(AiUsage::getOutputTokens).sum(),
                e.getValue().stream().map(AiUsage::getCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add)))
            .sorted(Comparator.comparingLong(Totals::calls).reversed())
            .toList();
    }

    private static UsageRow row(AiUsage u) {
        return new UsageRow(u.getId(), u.getOccurredAt(), u.getFeature(), u.getSubject(), u.getUsername(), u.getMode(),
            u.getModel(), u.getInputTokens(), u.getOutputTokens(), u.getCostUsd(), u.getDurationMs(), u.getOutcome(),
            u.getDetail());
    }

    /**
     * Estimated cost from list prices (input, output, cache reads). Mock calls cost nothing.
     */
    public BigDecimal cost(String mode, long input, long output, long cacheRead) {
        if (!"LIVE".equals(mode)) {
            return BigDecimal.ZERO;
        }
        AiProperties.Pricing p = props.pricing();
        BigDecimal usd = BigDecimal.valueOf(input).multiply(BigDecimal.valueOf(p.inputPerMtok()))
            .add(BigDecimal.valueOf(output).multiply(BigDecimal.valueOf(p.outputPerMtok())))
            .add(BigDecimal.valueOf(cacheRead).multiply(BigDecimal.valueOf(p.cacheReadPerMtok())));
        return usd.divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String feature, String subject, String username, String mode, String model, long input,
                       long output, long cacheRead, long durationMs, String outcome, String detail) {
        repository.save(new AiUsage(feature, subject, username, mode, model, input, output, cacheRead,
            cost(mode, input, output, cacheRead), durationMs, outcome, detail));
    }

    @Transactional(readOnly = true)
    public BigDecimal spentThisMonth() {
        return repository.costSince(startOfMonth());
    }

    @Transactional(readOnly = true)
    public long callsToday(String username) {
        return repository.countByUsernameAndOccurredAtGreaterThanEqual(username, startOfDay());
    }

    // ---------- administrator view ----------

    Instant startOfDay() {
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    Instant startOfMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Transactional(readOnly = true)
    public UsageReport report(int recentLimit) {
        List<AiUsage> month = repository.findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(startOfMonth());
        Instant today = startOfDay();
        BigDecimal spent = month.stream().map(AiUsage::getCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<UsageRow> recent = repository.findAllByOrderByOccurredAtDescIdDesc(Limit.of(Math.clamp(recentLimit, 1, 500)))
            .stream().map(AiUsageService::row).toList();
        return new UsageReport(spent, props.monthlyBudgetUsd(), month.size(),
            month.stream().filter(u -> !u.getOccurredAt().isBefore(today)).count(),
            totals(month, AiUsage::getFeature), totals(month, AiUsage::getUsername).stream().limit(10).toList(), recent);
    }

    public record UsageRow(Long id, Instant occurredAt, String feature, String subject, String username, String mode,
                           String model, long inputTokens, long outputTokens, BigDecimal costUsd, long durationMs,
                           String outcome, String detail) {
    }

    public record Totals(String key, long calls, long failures, long inputTokens, long outputTokens,
                         BigDecimal costUsd) {
    }

    public record UsageReport(BigDecimal spentThisMonthUsd, double monthlyBudgetUsd, long callsThisMonth,
                              long callsToday, List<Totals> byFeature, List<Totals> byUser, List<UsageRow> recent) {
    }
}
