package com.euronet.eft.portal.ai.insight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A stored AI result (JSON), generated once and reused: a build explanation, an executive digest. */
@Entity
@Table(name = "ai_insight")
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private String mode;

    private String model;

    private String masking;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    protected AiInsight() {
    }

    public AiInsight(String feature, String subject) {
        this.feature = feature;
        this.subject = subject;
    }

    /** Sets (or replaces, on regenerate) the result. */
    public void update(String content, String mode, String model, String masking, String createdBy) {
        this.content = content;
        this.mode = mode;
        this.model = model;
        this.masking = masking;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFeature() {
        return feature;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public String getMode() {
        return mode;
    }

    public String getModel() {
        return model;
    }

    public String getMasking() {
        return masking;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
