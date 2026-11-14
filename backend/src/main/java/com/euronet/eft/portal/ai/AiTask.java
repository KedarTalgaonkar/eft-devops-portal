package com.euronet.eft.portal.ai;

import java.util.function.Function;

/**
 * One request to the AI model with a typed (JSON-schema) answer.
 *
 * @param feature   e.g. "build-diagnosis"; used for usage, budgets and audit
 * @param subject   what it is about, e.g. "EFT/prepaid-qr-SIT#412"
 * @param type      record the answer is parsed into; its JSON schema constrains the model's output
 * @param system    instructions (stable text, cached)
 * @param input     the data to analyse; masked before it is sent
 * @param effort    how hard the model thinks
 * @param maxTokens upper bound for the answer
 * @param mock      answer used in MOCK mode, computed from the masked input (so mock mode exercises masking too)
 */
public record AiTask<T>(String feature, String subject, Class<T> type, String system, String input, Effort effort,
                        int maxTokens, Function<String, T> mock) {

    public enum Effort { LOW, MEDIUM, HIGH }
}
