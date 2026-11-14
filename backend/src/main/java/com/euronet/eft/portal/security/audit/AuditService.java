package com.euronet.eft.portal.security.audit;

import com.euronet.eft.portal.security.CurrentUser;
import com.euronet.eft.portal.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the security audit trail (sign-ins, sign-outs, lockouts, access denials, every change through the API).
 * Each event is committed in its own transaction so a failed business operation is still audited. Events are also
 * logged under the "AUDIT" logger for forwarding to a SIEM.
 */
@Service
public class AuditService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public record AuditView(Long id, Instant occurredAt, String username, String authSource, String action, String target,
                            String outcome, String ipAddress, String userAgent, String detail) {
    }

    private final AuditRepository repository;
    private final SecurityProperties props;

    public AuditService(AuditRepository repository, SecurityProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** Event by the signed-in user. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String target, String outcome, String detail) {
        CurrentUser u = CurrentUser.current();
        record(u == null ? "anonymous" : u.username(), u == null ? null : u.source().name(), action, target, outcome, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, String source, String action, String target, String outcome, String detail) {
        HttpServletRequest request = currentRequest();
        String ip = request == null ? null : request.getRemoteAddr();
        String agent = request == null ? null : request.getHeader("User-Agent");
        repository.save(new AuditEvent(username, source, action, target, outcome, ip, agent, detail));
        audit.info("user={} source={} action={} target={} outcome={} ip={} detail={}",
                username, source, action, target, outcome, ip, detail);
    }

    @Transactional(readOnly = true)
    public List<AuditView> recent(int limit) {
        return repository.findAllByOrderByOccurredAtDescIdDesc(Limit.of(Math.clamp(limit, 1, 5000))).stream()
                .map(e -> new AuditView(e.getId(), e.getOccurredAt(), e.getUsername(), e.getAuthSource(), e.getAction(),
                        e.getTarget(), e.getOutcome(), e.getIpAddress(), e.getUserAgent(), e.getDetail()))
                .toList();
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteOlderThan(Instant.now().minus(props.auditRetention()));
        if (removed > 0) {
            audit.info("Audit retention: removed {} events older than {}", removed, props.auditRetention());
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs ? attrs.getRequest() : null;
    }
}
