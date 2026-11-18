package com.euronet.eft.portal.inventory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An EFT application for one client, e.g. "IMPS Portal" for "COSMOS BANK".
 */
@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String client = "";

    private String owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("environment ASC, contextName ASC")
    private List<Deployment> deployments = new ArrayList<>();

    /**
     * Source repositories (GitHub "org/name"); any number per application.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "application_repository", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "repository", nullable = false)
    @OrderBy
    private Set<String> repositories = new LinkedHashSet<>();

    protected Application() {
    }

    public Application(String name, String client, String owner) {
        this.name = name;
        this.client = client == null ? "" : client;
        this.owner = owner;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Deployment addDeployment(Deployment deployment) {
        deployment.setApplication(this);
        deployments.add(deployment);
        return deployment;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getClient() {
        return client;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setClient(String client) {
        this.client = client == null ? "" : client;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Deployment> getDeployments() {
        return deployments;
    }

    public Set<String> getRepositories() {
        return repositories;
    }
}
