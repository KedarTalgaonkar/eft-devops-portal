package com.euronet.eft.portal.security;

import com.euronet.eft.portal.security.local.LocalUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The signed-in user as the rest of the portal sees it, whether they came through SSO or a local account.
 */
public record CurrentUser(String username, String displayName, String email, Set<PortalRole> roles, AuthSource source,
                          boolean mustChangePassword) implements Serializable {

    public static CurrentUser from(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Set<PortalRole> roles = EnumSet.noneOf(PortalRole.class);
        for (GrantedAuthority a : auth.getAuthorities()) {
            PortalRole.parse(a.getAuthority()).ifPresent(roles::add);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            String username = firstNonBlank(oidc.getPreferredUsername(), oidc.getEmail(), oidc.getSubject());
            return new CurrentUser(username, firstNonBlank(oidc.getFullName(), username), oidc.getEmail(), roles,
                AuthSource.SSO, false);
        }
        if (principal instanceof LocalUserDetails local) {
            return new CurrentUser(local.getUsername(), local.displayName(), local.email(), roles, AuthSource.LOCAL,
                local.mustChangePassword());
        }
        return new CurrentUser(auth.getName(), auth.getName(), null, roles, AuthSource.LOCAL, false);
    }

    public static CurrentUser current() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Username for audit columns: the signed-in user, or "system" for scheduled jobs and startup tasks.
     */
    public static String nameOrSystem() {
        CurrentUser u = current();
        return u == null ? "system" : u.username();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "unknown";
    }

    public List<String> roleNames() {
        return roles.stream().map(Enum::name).toList();
    }

    public enum AuthSource {SSO, LOCAL, SYSTEM}
}
