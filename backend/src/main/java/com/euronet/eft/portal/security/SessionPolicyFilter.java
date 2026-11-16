package com.euronet.eft.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Session rules on top of the idle timeout:
 * <ul>
 *   <li>absolute lifetime: a session older than {@code portal.security.absolute-session-timeout} is ended, however active</li>
 *   <li>a local account with a temporary password can only use /api/auth/** until the password is changed</li>
 * </ul>
 */
final class SessionPolicyFilter extends OncePerRequestFilter {

    static final String AUTHENTICATED_AT = "PORTAL_AUTHENTICATED_AT";

    private final Duration absoluteTimeout;

    SessionPolicyFilter(Duration absoluteTimeout) {
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        CurrentUser user = CurrentUser.current();
        HttpSession session = request.getSession(false);
        if (user != null && session != null) {
            Object at = session.getAttribute(AUTHENTICATED_AT);
            if (at instanceof Instant since && since.plus(absoluteTimeout).isBefore(Instant.now())) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                JsonErrors.write(response, 401, "Unauthorized", "SESSION_EXPIRED",
                    "Your session has expired. Please sign in again.", request.getRequestURI());
                return;
            }
            String uri = request.getRequestURI();
            if (user.mustChangePassword() && uri.startsWith("/api/") && !uri.startsWith("/api/auth/")) {
                JsonErrors.write(response, 403, "Forbidden", "PASSWORD_CHANGE_REQUIRED",
                    "Change your temporary password before continuing.", uri);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
