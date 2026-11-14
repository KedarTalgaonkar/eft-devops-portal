package com.euronet.eft.portal.ai.insight;

import com.euronet.eft.portal.ai.AiOutcome;
import com.euronet.eft.portal.common.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Stores AI results as JSON, reads them back typed, and keeps the thumbs-up / thumbs-down feedback. */
@Service
public class AiInsightService {

    private final AiInsightRepository repository;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AiInsightService(AiInsightRepository repository, JdbcClient jdbc, JsonMapper json) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * A stored result for the UI.
     *
     * @param myVote the current user's vote: 1, -1 or null
     */
    public record Stored<T>(long id, String feature, String subject, T content, String mode, String model,
                            String masking, Instant createdAt, String createdBy, int helpful, int notHelpful,
                            Integer myVote) {
    }

    @Transactional(readOnly = true)
    public <T> Optional<Stored<T>> find(String feature, String subject, Class<T> type, String username) {
        return repository.findByFeatureAndSubject(feature, subject).map(i -> view(i, type, username));
    }

    @Transactional(readOnly = true)
    public <T> List<Stored<T>> latest(String feature, Class<T> type, int limit, String username) {
        return repository.findByFeatureOrderByCreatedAtDescIdDesc(feature, Limit.of(Math.clamp(limit, 1, 100))).stream()
                .map(i -> view(i, type, username)).toList();
    }

    /** Category counts of earlier explanations whose subject starts with the prefix (e.g. the same Jenkins job). */
    @Transactional(readOnly = true)
    public List<AiInsight> withSubjectPrefix(String feature, String prefix, int limit) {
        return repository.findByFeatureAndSubjectStartingWithOrderByCreatedAtDesc(feature, prefix, Limit.of(limit));
    }

    public <T> T read(AiInsight insight, Class<T> type) {
        return json.readValue(insight.getContent(), type);
    }

    /** Saves a new result, or replaces the existing one for the same feature and subject. */
    @Transactional
    public <T> Stored<T> save(String feature, String subject, AiOutcome<T> outcome, String username, Class<T> type) {
        AiInsight insight = repository.findByFeatureAndSubject(feature, subject).orElseGet(() -> new AiInsight(feature, subject));
        boolean replaced = insight.getId() != null;
        insight.update(json.writeValueAsString(outcome.value()), outcome.mode(), outcome.model(), outcome.masking(), username);
        try {
            insight = repository.saveAndFlush(insight);
        } catch (DataIntegrityViolationException e) {
            // two users asked at the same moment; keep the first answer
            return find(feature, subject, type, username).orElseThrow(() -> e);
        }
        if (replaced) {
            jdbc.sql("delete from ai_feedback where insight_id = ?").param(insight.getId()).update();
        }
        return view(insight, type, username);
    }

    /** vote: 1 helpful, -1 not helpful, 0 withdraw. */
    @Transactional
    public <T> Stored<T> vote(long id, int vote, String username, Class<T> type) {
        AiInsight insight = repository.findById(id).orElseThrow(() -> new NotFoundException("AI result", id));
        if (vote == 0) {
            jdbc.sql("delete from ai_feedback where insight_id = ? and username = ?").params(id, username).update();
        } else {
            jdbc.sql("""
                    insert into ai_feedback (insight_id, username, vote) values (?, ?, ?)
                    on conflict (insight_id, username) do update set vote = excluded.vote, created_at = now()""")
                    .params(id, username, vote > 0 ? 1 : -1).update();
        }
        return view(insight, type, username);
    }

    @Transactional(readOnly = true)
    public AiInsight get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("AI result", id));
    }

    /** Helpful / not helpful per feature, for the administrator page. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> feedbackByFeature() {
        return jdbc.sql("""
                select i.feature as feature,
                       count(*) filter (where f.vote = 1)  as helpful,
                       count(*) filter (where f.vote = -1) as not_helpful
                from ai_feedback f join ai_insight i on i.id = f.insight_id
                group by i.feature order by i.feature""").query().listOfRows();
    }

    private <T> Stored<T> view(AiInsight i, Class<T> type, String username) {
        Map<String, Object> f = jdbc.sql("""
                select count(*) filter (where vote = 1)  as up,
                       count(*) filter (where vote = -1) as down,
                       max(case when username = ? then vote end) as mine
                from ai_feedback where insight_id = ?""").params(username, i.getId()).query().singleRow();
        Number mine = (Number) f.get("mine");
        return new Stored<>(i.getId(), i.getFeature(), i.getSubject(), read(i, type), i.getMode(), i.getModel(),
                i.getMasking(), i.getCreatedAt(), i.getCreatedBy(), ((Number) f.get("up")).intValue(),
                ((Number) f.get("down")).intValue(), mine == null ? null : mine.intValue());
    }
}
