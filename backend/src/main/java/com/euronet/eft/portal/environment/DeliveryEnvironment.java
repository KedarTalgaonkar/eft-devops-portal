package com.euronet.eft.portal.environment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** A delivery environment (DEVELOP, SIT, UAT, PERF, PROD, ...). Rows are added automatically on import. */
@Entity
@Table(name = "environment")
public class DeliveryEnvironment {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String category = "NON_PROD";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeliveryEnvironment() {
    }

    public DeliveryEnvironment(String code, String name, int sortOrder, String category) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.category = category;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
