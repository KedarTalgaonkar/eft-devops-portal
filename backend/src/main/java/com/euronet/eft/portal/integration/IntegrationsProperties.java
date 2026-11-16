package com.euronet.eft.portal.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.integrations")
public record IntegrationsProperties(
    ToolProperties jenkins,
    ToolProperties github,
    ToolProperties sonar,
    ToolProperties artifactory,
    ToolProperties monitoring,
    ToolProperties sbom) {
}
