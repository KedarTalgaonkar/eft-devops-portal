package com.euronet.eft.portal.announcement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A message shown in the header of every page between {@code startsAt} and {@code endsAt}. */
@Entity
@Table(name = "announcement")
public class Announcement {

    /** Decides the banner colour and whether users may hide it (INCIDENT cannot be hidden). */
    public enum Kind { INFO, RELEASE, MAINTENANCE, INCIDENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private String message;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(name = "link_label")
    private String linkLabel;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Announcement() {
    }

    public Announcement(String createdBy) {
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void set(Kind kind, String message, String linkUrl, String linkLabel, Instant startsAt, Instant endsAt) {
        this.kind = kind;
        this.message = message;
        this.linkUrl = linkUrl;
        this.linkLabel = linkLabel;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void touched(String by) {
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }

    public void endNow(String by) {
        Instant now = Instant.now();
        this.endsAt = now.isAfter(startsAt) ? now : startsAt.plusSeconds(1);
        touched(by);
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getMessage() {
        return message;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public String getLinkLabel() {
        return linkLabel;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
