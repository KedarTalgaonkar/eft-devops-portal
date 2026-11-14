package com.euronet.eft.portal.security.audit;

import com.euronet.eft.portal.security.audit.AuditService.AuditView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrators only (see SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Administration", description = "Security audit trail and local accounts")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Latest audit events: sign-ins, sign-outs, lockouts, access denials and changes")
    public List<AuditView> recent(@RequestParam(defaultValue = "500") int limit) {
        return audit.recent(limit);
    }
}
