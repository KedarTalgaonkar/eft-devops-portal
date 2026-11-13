package com.euronet.eft.portal.integration;

/** Implemented by every tool client, so the dashboard can show which integrations are up and in which mode. */
public interface IntegrationHealth {

    IntegrationStatus status();
}
