package com.euronet.eft.portal.security.audit;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllByOrderByOccurredAtDescIdDesc(Limit limit);

    @Modifying
    @Query("delete from AuditEvent e where e.occurredAt < ?1")
    int deleteOlderThan(Instant cutoff);
}
