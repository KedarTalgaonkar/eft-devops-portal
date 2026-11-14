package com.euronet.eft.portal.ai;

/**
 * A successful AI answer, with masked values already restored.
 *
 * @param mode    MOCK or LIVE
 * @param model   model id, or "sample" in mock mode
 * @param masking what was masked before sending, e.g. "2 IP addresses, 1 secret masked"
 */
public record AiOutcome<T>(T value, String mode, String model, String masking) {
}
