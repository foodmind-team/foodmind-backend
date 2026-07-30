package com.foodmind.foodmindbackend.recommendation.domain.agent;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public record ValidatedAgentResult(
        String agentContractVersion,
        String modelVersion,
        String modelStatus,
        String featureSchemaVersion,
        String agentTraceId,
        List<ValidatedAgentCandidate> candidates) {

    public ValidatedAgentResult {
        candidates = List.copyOf(candidates);
    }
}
