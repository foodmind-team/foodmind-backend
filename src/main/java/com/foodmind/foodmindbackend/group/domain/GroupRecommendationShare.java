package com.foodmind.foodmindbackend.group.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupRecommendationShare(
        UUID id,
        UUID groupId,
        UUID sharedByUserId,
        UUID recommendationCandidateId,
        String message,
        OffsetDateTime createdAt) {
}
