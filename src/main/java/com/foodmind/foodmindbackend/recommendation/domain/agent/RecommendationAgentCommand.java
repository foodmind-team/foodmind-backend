package com.foodmind.foodmindbackend.recommendation.domain.agent;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record RecommendationAgentCommand(
        String contractVersion,
        UUID requestId,
        UUID sessionId,
        String traceId,
        OffsetDateTime deadlineAt,
        Map<String, Object> requestContext,
        Map<String, Object> preferenceContext,
        List<RecommendationAgentCandidate> candidates) {

    public RecommendationAgentCommand {
        requestContext = Collections.unmodifiableMap(new LinkedHashMap<>(requestContext));
        preferenceContext = Collections.unmodifiableMap(new LinkedHashMap<>(preferenceContext));
        candidates = List.copyOf(candidates);
    }
}
