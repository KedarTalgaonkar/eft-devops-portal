package com.euronet.eft.portal.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Audits every change made through the API (POST / PUT / PATCH / DELETE), with the HTTP status as outcome.
 * Registered by WebConfig (not a component, so MVC test slices without the audit store still start).
 */
public class ApiAuditInterceptor implements HandlerInterceptor {

    private static final Set<String> CHANGES = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService audit;

    public ApiAuditInterceptor(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String uri = request.getRequestURI();
        // sign-in, sign-out and password changes are audited explicitly with more detail
        if (!CHANGES.contains(request.getMethod()) || uri.startsWith("/api/auth/")) {
            return;
        }
        int status = response.getStatus();
        audit.record("API_" + request.getMethod(), uri, status < 400 && ex == null ? "SUCCESS" : "FAILURE", "HTTP " + status);
    }
}
