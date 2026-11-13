package com.euronet.eft.portal.integration;

/**
 * Connection settings for one external tool. Secrets come from environment variables only.
 *
 * @param secondaryUrl a second endpoint, for modules that talk to two services (monitoring: Prometheus)
 */
public record ToolProperties(IntegrationMode mode, String baseUrl, String username, String token, String secondaryUrl) {

    public ToolProperties {
        mode = mode == null ? IntegrationMode.MOCK : mode;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
