package com.foodmind.foodmindbackend.recommendation.application.port;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public interface RecommendationContextQuery {

    RecommendationContext load(UUID userId, RecommendationRequestContext request);

    boolean activeGroupMember(UUID userId, UUID groupId);
}
