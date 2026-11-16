package com.euronet.eft.portal.ai;

import com.euronet.eft.portal.ai.insight.AiInsightService;
import com.euronet.eft.portal.ai.usage.AiUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Administrators: AI status, usage and estimated cost this month, recent calls, and user feedback.
 */
@RestController
@RequestMapping("/api/admin/ai")
@Tag(name = "Administration")
public class AiAdminController {

    private final AiGateway gateway;
    private final AiUsageService usage;
    private final AiInsightService insights;
    private final AiProperties props;

    public AiAdminController(AiGateway gateway, AiUsageService usage, AiInsightService insights, AiProperties props) {
        this.gateway = gateway;
        this.usage = usage;
        this.insights = insights;
        this.props = props;
    }

    @GetMapping
    @Operation(summary = "AI settings in effect, usage and cost this month, recent calls and feedback")
    public AiAdminView view(@RequestParam(defaultValue = "50") int recent) {
        return new AiAdminView(gateway.status(), usage.report(recent), insights.feedbackByFeature(), props.maskDomains(),
            props.maxLogKb(), props.digest().enabled() ? props.digest().cron() + " (" + props.digest().zone() + ")" : "off");
    }

    public record AiAdminView(AiGateway.AiStatus status, AiUsageService.UsageReport usage,
                              List<Map<String, Object>> feedback, List<String> maskDomains, int maxLogKb,
                              String digestSchedule) {
    }
}
