package com.euronet.eft.portal.integration;

/**
 * One entry of the dashboard's integration strip.
 */
public record IntegrationStatus(String key, String name, IntegrationMode mode, boolean reachable, String endpoint,
                                String message) {

    public static IntegrationStatus mock(String key, String name) {
        return new IntegrationStatus(key, name, IntegrationMode.MOCK, true, null, "Serving generated sample data");
    }
}
