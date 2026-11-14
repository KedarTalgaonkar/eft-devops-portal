package com.euronet.eft.portal.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Portal roles, hierarchical: ADMIN includes EDITOR includes VIEWER.
 * <ul>
 *   <li>VIEWER: read every dashboard</li>
 *   <li>EDITOR: import the inventory, edit Jenkins jobs and repositories, run SBOM scans and health probes</li>
 *   <li>ADMIN: manage environments, local users, the audit log and the API docs</li>
 * </ul>
 */
public enum PortalRole {
    VIEWER, EDITOR, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<PortalRole> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(Locale.ROOT);
        if (v.startsWith("ROLE_")) {
            v = v.substring(5);
        }
        try {
            return Optional.of(valueOf(v));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
