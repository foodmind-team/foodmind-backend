package com.foodmind.foodmindbackend.group.api.response;

import com.foodmind.foodmindbackend.group.domain.GroupRecommendationShare;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupRecommendationShareResponse(
        UUID id,
        UUID groupId,
        UUID sharedByUserId,
        UUID recommendationCandidateId,
        String message,
        OffsetDateTime createdAt) {

    public static GroupRecommendationShareResponse from(GroupRecommendationShare share) {
        return new GroupRecommendationShareResponse(
                share.id(),
                share.groupId(),
                share.sharedByUserId(),
                share.recommendationCandidateId(),
                share.message(),
                share.createdAt());
    }
}
