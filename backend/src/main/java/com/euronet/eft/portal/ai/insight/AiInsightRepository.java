package com.euronet.eft.portal.ai.insight;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    Optional<AiInsight> findByFeatureAndSubject(String feature, String subject);

    List<AiInsight> findByFeatureOrderByCreatedAtDescIdDesc(String feature, Limit limit);

    List<AiInsight> findByFeatureAndSubjectStartingWithOrderByCreatedAtDesc(String feature, String subjectPrefix, Limit limit);
}
