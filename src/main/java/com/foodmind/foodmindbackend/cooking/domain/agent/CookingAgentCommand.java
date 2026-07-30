package com.foodmind.foodmindbackend.cooking.domain.agent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public record CookingAgentCommand(
        String contractVersion,
        UUID requestId,
        UUID planId,
        String traceId,
        OffsetDateTime deadlineAt,
        Map<String, Object> requestSnapshot,
        Map<String, Object> preferenceSnapshot,
        List<CookingAgentCandidate> candidates) {

    public CookingAgentCommand {
        requestSnapshot = requestSnapshot == null ? Map.of() : Map.copyOf(requestSnapshot);
        preferenceSnapshot = preferenceSnapshot == null ? Map.of() : Map.copyOf(preferenceSnapshot);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
