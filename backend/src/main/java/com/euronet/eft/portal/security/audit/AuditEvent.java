package com.euronet.eft.portal.security.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private String username;

    @Column(name = "auth_source")
    private String authSource;

    @Column(nullable = false)
    private String action;

    private String target;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    private String detail;

    protected AuditEvent() {
    }

    public AuditEvent(String username, String authSource, String action, String target, String outcome,
                      String ipAddress, String userAgent, String detail) {
        this.occurredAt = Instant.now();
        this.username = cut(username, 200);
        this.authSource = cut(authSource, 20);
        this.action = cut(action, 60);
        this.target = cut(target, 500);
        this.outcome = cut(outcome, 20);
        this.ipAddress = cut(ipAddress, 64);
        this.userAgent = cut(userAgent, 300);
        this.detail = cut(detail, 1000);
    }

    private static String cut(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getUsername() {
        return username;
    }

    public String getAuthSource() {
        return authSource;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDetail() {
        return detail;
    }
}
