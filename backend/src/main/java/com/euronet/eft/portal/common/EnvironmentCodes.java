package com.euronet.eft.portal.common;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Environment codes are plain upper-case strings (DEVELOP, SIT, UAT, PERF, PROD, ...), stored in the
 * {@code environment} table. This turns whatever is typed in a sheet header or query parameter into a code.
 */
public final class EnvironmentCodes {

    private static final Map<String, String> ALIASES = Map.of(
            "DEV", "DEVELOP",
            "DEVELOPMENT", "DEVELOP",
            "PRODUCTION", "PROD",
            "PRE_PROD", "PREPROD",
            "PRE_PRODUCTION", "PREPROD",
            "PERFORMANCE", "PERF",
            "STAGING", "STAGE");

    private EnvironmentCodes() {
    }

    /** " sit " -> SIT, "Pre-Prod" -> PREPROD, "Dev" -> DEVELOP; blank -> empty. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String code = raw.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_+)|(_+$)", "");
        if (code.isEmpty() || code.length() > 32) {
            return Optional.empty();
        }
        return Optional.of(ALIASES.getOrDefault(code, code));
    }

    public static String require(String raw) {
        return normalize(raw).orElseThrow(() -> new IllegalArgumentException("Invalid environment: " + raw));
    }
}
