package com.euronet.eft.portal.ai.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    @Query("select coalesce(sum(u.costUsd), 0) from AiUsage u where u.occurredAt >= :since")
    BigDecimal costSince(@Param("since") Instant since);

    long countByUsernameAndOccurredAtGreaterThanEqual(String username, Instant since);

    List<AiUsage> findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(Instant since);

    List<AiUsage> findAllByOrderByOccurredAtDescIdDesc(Limit limit);
}
