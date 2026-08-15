package com.foodmind.foodmindbackend.recommendation.api.response;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionFactor;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionMode;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionProfile;
import java.util.List;

public record RecommendationDecisionProfileResponse(
        RecommendationDecisionMode mode,
        List<RecommendationDecisionFactor> appliedFactors,
        int groupMemberEvidenceCount) {

    public static RecommendationDecisionProfileResponse from(RecommendationDecisionProfile profile) {
        return new RecommendationDecisionProfileResponse(
                profile.mode(), profile.appliedFactors(), profile.groupMemberEvidenceCount());
    }
}
