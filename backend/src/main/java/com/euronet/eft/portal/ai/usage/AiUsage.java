package com.euronet.eft.portal.ai.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One AI call (live or mock) in the usage and cost ledger.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String feature;

    private String subject;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String mode;

    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @Column(name = "cost_usd", nullable = false)
    private BigDecimal costUsd;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private String outcome;

    private String detail;

    protected AiUsage() {
    }

    public AiUsage(String feature, String subject, String username, String mode, String model, long inputTokens,
                   long outputTokens, long cacheReadTokens, BigDecimal costUsd, long durationMs, String outcome,
                   String detail) {
        this.occurredAt = Instant.now();
        this.feature = feature;
        this.subject = truncate(subject, 300);
        this.username = username;
        this.mode = mode;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.costUsd = costUsd;
        this.durationMs = durationMs;
        this.outcome = outcome;
        this.detail = truncate(detail, 500);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getFeature() {
        return feature;
    }

    public String getSubject() {
        return subject;
    }

    public String getUsername() {
        return username;
    }

    public String getMode() {
        return mode;
    }

    public String getModel() {
        return model;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getCacheReadTokens() {
        return cacheReadTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }
}
