package com.euronet.eft.portal.ai;

import com.euronet.eft.portal.ai.builds.BuildDiagnosis;
import com.euronet.eft.portal.ai.builds.BuildExplanationService;
import com.euronet.eft.portal.ai.builds.BuildExplanationService.BuildExplanation;
import com.euronet.eft.portal.ai.digest.DigestService;
import com.euronet.eft.portal.ai.digest.ExecutiveDigest;
import com.euronet.eft.portal.ai.insight.AiInsight;
import com.euronet.eft.portal.ai.insight.AiInsightService;
import com.euronet.eft.portal.ai.insight.AiInsightService.Stored;
import com.euronet.eft.portal.common.EnvironmentCodes;
import com.euronet.eft.portal.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI features. Reading is open to every signed-in user; asking for a build explanation and voting too (within the
 * daily limit); regenerating needs EDITOR; generating a digest on demand needs ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "Build failure explanations and the executive digest (masked data, usage-limited)")
public class AiController {

    private final AiGateway gateway;
    private final BuildExplanationService builds;
    private final DigestService digests;
    private final AiInsightService insights;

    public AiController(AiGateway gateway, BuildExplanationService builds, DigestService digests, AiInsightService insights) {
        this.gateway = gateway;
        this.builds = builds;
        this.digests = digests;
        this.insights = insights;
    }

    @GetMapping("/status")
    @Operation(summary = "Whether AI is on, in which mode, and with which model and provider")
    public AiGateway.AiStatus status() {
        return gateway.status();
    }

    @GetMapping("/builds/{applicationId}/{env}/{number}")
    @Operation(summary = "The stored explanation of a failed build (204 when it has not been explained yet)")
    public ResponseEntity<BuildExplanation> explanation(@PathVariable long applicationId, @PathVariable String env,
                                                        @PathVariable int number) {
        return builds.find(applicationId, EnvironmentCodes.require(env), number)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/builds/{applicationId}/{env}/{number}/explain")
    @Operation(summary = "Explain why a build failed (reuses the stored explanation unless refresh=true, editors only)")
    public BuildExplanation explain(@PathVariable long applicationId, @PathVariable String env, @PathVariable int number,
                                    @RequestParam(defaultValue = "false") boolean refresh) {
        return builds.explain(applicationId, EnvironmentCodes.require(env), number, refresh);
    }

    @GetMapping("/digest/latest")
    @Operation(summary = "The latest executive digest (204 when none has been written yet)")
    public ResponseEntity<Stored<ExecutiveDigest>> latestDigest() {
        return digests.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/digest")
    @Operation(summary = "Earlier executive digests, newest first")
    public List<Stored<ExecutiveDigest>> digestHistory(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return digests.history(limit);
    }

    @PostMapping("/digest")
    @Operation(summary = "Write a new executive digest now (administrators)")
    public Stored<ExecutiveDigest> generateDigest() {
        return digests.generateNow();
    }

    @PostMapping("/insights/{id}/feedback")
    @Operation(summary = "Rate an AI answer: 1 helpful, -1 not helpful, 0 withdraw")
    public Stored<?> feedback(@PathVariable long id, @RequestBody Vote vote) {
        if (vote.vote() < -1 || vote.vote() > 1) {
            throw new AiException(HttpStatus.BAD_REQUEST, "vote must be -1, 0 or 1");
        }
        AiInsight insight = insights.get(id);
        Class<?> type = switch (insight.getFeature()) {
            case BuildExplanationService.FEATURE -> BuildDiagnosis.class;
            case DigestService.FEATURE -> ExecutiveDigest.class;
            default -> throw new AiException(HttpStatus.BAD_REQUEST, "Unknown AI result type");
        };
        return insights.vote(id, vote.vote(), CurrentUser.nameOrSystem(), type);
    }

    public record Vote(@Min(-1) @Max(1) int vote) {
    }
}
