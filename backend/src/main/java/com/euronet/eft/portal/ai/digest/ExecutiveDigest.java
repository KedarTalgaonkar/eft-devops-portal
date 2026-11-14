package com.euronet.eft.portal.ai.digest;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** A plain-English executive summary of delivery, quality, security and operations. Its JSON schema constrains the model. */
public record ExecutiveDigest(
        @JsonPropertyDescription("One sentence, at most 20 words, stating the overall situation")
        String headline,
        @JsonPropertyDescription("Overall status: GREEN on track, AMBER needs attention, RED action required")
        Rag overall,
        @JsonPropertyDescription("Three to five sentences for senior management: what changed versus the previous period and why it matters")
        String summary,
        @JsonPropertyDescription("Up to 4 things that got better, each one short sentence with the number")
        List<String> improvements,
        @JsonPropertyDescription("Up to 5 most important risks, most severe first")
        List<Risk> risks,
        @JsonPropertyDescription("Up to 5 concrete next actions, each with an owner taken from the data when known")
        List<Action> actions,
        @JsonPropertyDescription("One line per area (Delivery, Service health, Code quality, Security, Operations)")
        List<AreaNote> areas) {

    public enum Rag { GREEN, AMBER, RED }

    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW }

    public record Risk(
            @JsonPropertyDescription("Short title of the risk")
            String title,
            @JsonPropertyDescription("Area: Delivery, Service health, Code quality, Security or Operations")
            String area,
            Severity severity,
            @JsonPropertyDescription("One sentence: impact and the evidence (numbers) behind it")
            String detail) {
    }

    public record Action(
            @JsonPropertyDescription("What to do, starting with a verb")
            String action,
            @JsonPropertyDescription("Team or person responsible, from the data; 'Unassigned' if unknown")
            String owner,
            @JsonPropertyDescription("Area the action belongs to")
            String area) {
    }

    public record AreaNote(String area, Rag status,
                           @JsonPropertyDescription("One sentence with the key number")
                           String note) {
    }
}
