package com.euronet.eft.portal.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One recorded inventory change: an owner move, a new deployment path, a URL or version change, a portal edit. */
@Entity
@Table(name = "inventory_change")
public class InventoryChange {

    public enum Type { APPLICATION_ADDED, DEPLOYMENT_ADDED, FIELD_CHANGED }

    public enum Source { IMPORT, PORTAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "deployment_id")
    private Long deploymentId;

    @Column(name = "change_type", nullable = false)
    private String changeType;

    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(nullable = false)
    private String source;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected InventoryChange() {
    }

    public InventoryChange(Long applicationId, Long deploymentId, Type type, String field, String oldValue,
                           String newValue, Source source) {
        this.applicationId = applicationId;
        this.deploymentId = deploymentId;
        this.changeType = type.name();
        this.field = field;
        this.oldValue = truncate(oldValue);
        this.newValue = truncate(newValue);
        this.source = source.name();
        this.changedAt = Instant.now();
    }

    private static String truncate(String v) {
        return v == null || v.length() <= 500 ? v : v.substring(0, 500);
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getDeploymentId() {
        return deploymentId;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getSource() {
        return source;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
