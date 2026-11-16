package com.euronet.eft.portal.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One deployment path: a context of an application in one environment (a row's SIT or UAT block in the Excel).
 * The environment is a code from the {@code environment} table, so new environments need no code change.
 */
@Entity
@Table(name = "deployment")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 32)
    private String environment;

    @Column(name = "context_name", nullable = false)
    private String contextName = "";

    @Column(nullable = false, length = 30)
    private String platform = "TOMCAT";

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "tomcat_version")
    private String tomcatVersion;

    @Column(name = "java_version")
    private String javaVersion;

    @Column(name = "tomcat_admin_done", nullable = false)
    private boolean tomcatAdminDone;

    @Column(name = "build_xml_done", nullable = false)
    private boolean buildXmlDone;

    @Column(name = "jenkins_job")
    private String jenkinsJob;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Deployment() {
    }

    public Deployment(String environment, String contextName) {
        this.environment = environment;
        this.contextName = contextName == null ? "" : contextName;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    void setApplication(Application application) {
        this.application = application;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getContextName() {
        return contextName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTomcatVersion() {
        return tomcatVersion;
    }

    public void setTomcatVersion(String tomcatVersion) {
        this.tomcatVersion = tomcatVersion;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public boolean isTomcatAdminDone() {
        return tomcatAdminDone;
    }

    public void setTomcatAdminDone(boolean tomcatAdminDone) {
        this.tomcatAdminDone = tomcatAdminDone;
    }

    public boolean isBuildXmlDone() {
        return buildXmlDone;
    }

    public void setBuildXmlDone(boolean buildXmlDone) {
        this.buildXmlDone = buildXmlDone;
    }

    public String getJenkinsJob() {
        return jenkinsJob;
    }

    public void setJenkinsJob(String jenkinsJob) {
        this.jenkinsJob = jenkinsJob;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Full URL of the deployed context, e.g. http://host:9091/SITCBCQRAPI/
     */
    public String contextUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return contextName.isBlank() ? baseUrl + "/" : baseUrl + "/" + contextName + "/";
    }
}
