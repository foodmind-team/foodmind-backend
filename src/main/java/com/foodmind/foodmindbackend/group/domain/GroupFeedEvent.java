package com.foodmind.foodmindbackend.group.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupFeedEvent(
        FeedSourceType sourceType,
        UUID sourceId,
        OffsetDateTime occurredAt,
        UUID actorUserId,
        String actorDisplayName,
        UUID foodRecordId,
        String mealNameSnapshot,
        UUID recommendationShareId,
        UUID recommendationCandidateId,
        String message) {
}
