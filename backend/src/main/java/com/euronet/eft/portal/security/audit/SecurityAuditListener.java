package com.euronet.eft.portal.security.audit;

import com.euronet.eft.portal.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Audits access denials of signed-in users (anonymous 401s are expected and not stored).
 */
@Component
public class SecurityAuditListener {

    private final AuditService audit;

    public SecurityAuditListener(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> event) {
        Authentication auth = event.getAuthentication().get();
        CurrentUser user = CurrentUser.from(auth);
        if (user == null) {
            return;
        }
        String target = event.getSource() instanceof HttpServletRequest r ? r.getMethod() + " " + r.getRequestURI() : String.valueOf(event.getSource());
        audit.record(user.username(), user.source().name(), "ACCESS_DENIED", target, "DENIED", "roles " + user.roleNames());
    }
}
