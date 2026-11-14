package com.euronet.eft.portal.security;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings ({@code portal.security.*}). Sign-in itself (OIDC client id, secret, issuer) is standard Spring
 * configuration under {@code spring.security.oauth2.client}, see the sso-entra / sso-dev profiles.
 *
 * @param localLoginEnabled       allow username + password accounts stored in the portal database
 * @param ssoLabel                text of the single sign-on button
 * @param registrationId          OAuth2 client registration used for SSO
 * @param defaultRole             role for SSO users with no mapped role; empty = such users are refused
 * @param roleMapping             IdP role / group value (Entra app role or group object id) to portal role
 * @param absoluteSessionTimeout  maximum session age regardless of activity (idle timeout: spring.session.timeout)
 * @param cookieSecure            mark session and XSRF cookies Secure (must be true behind HTTPS)
 * @param auditRetention          how long audit events are kept
 */
@ConfigurationProperties("portal.security")
public record SecurityProperties(
        Boolean localLoginEnabled,
        String ssoLabel,
        String registrationId,
        String defaultRole,
        Map<String, String> roleMapping,
        Duration absoluteSessionTimeout,
        Boolean cookieSecure,
        Duration auditRetention,
        Lockout lockout,
        Oidc oidc,
        BootstrapAdmin bootstrapAdmin) {

    public SecurityProperties {
        localLoginEnabled = localLoginEnabled == null || localLoginEnabled;
        ssoLabel = ssoLabel == null || ssoLabel.isBlank() ? "Sign in with Microsoft" : ssoLabel;
        registrationId = registrationId == null || registrationId.isBlank() ? "sso" : registrationId;
        roleMapping = roleMapping == null ? new LinkedHashMap<>() : roleMapping;
        absoluteSessionTimeout = absoluteSessionTimeout == null ? Duration.ofHours(10) : absoluteSessionTimeout;
        cookieSecure = cookieSecure != null && cookieSecure;
        auditRetention = auditRetention == null ? Duration.ofDays(365) : auditRetention;
        lockout = lockout == null ? new Lockout(null, null) : lockout;
        oidc = oidc == null ? new Oidc(null, null) : oidc;
        bootstrapAdmin = bootstrapAdmin == null ? new BootstrapAdmin(null, null) : bootstrapAdmin;
    }

    /** Lock a local account for {@code duration} after {@code maxAttempts} consecutive failed sign-ins. */
    public record Lockout(Integer maxAttempts, Duration duration) {
        public Lockout {
            maxAttempts = maxAttempts == null ? 5 : maxAttempts;
            duration = duration == null ? Duration.ofMinutes(15) : duration;
        }
    }

    /**
     * @param expectedIssuer  required "iss" of ID tokens when the provider is configured without issuer-uri discovery
     * @param endSessionUri   RP-initiated logout endpoint when discovery does not provide one
     */
    public record Oidc(String expectedIssuer, String endSessionUri) {
    }

    /** First administrator, created only when no local account exists; must change the password at first sign-in. */
    public record BootstrapAdmin(String username, String password) {
        public BootstrapAdmin {
            username = username == null || username.isBlank() ? "admin" : username.strip();
        }
    }
}
