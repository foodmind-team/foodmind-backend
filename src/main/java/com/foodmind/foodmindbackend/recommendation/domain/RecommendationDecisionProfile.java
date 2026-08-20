package com.foodmind.foodmindbackend.recommendation.domain;

import java.util.ArrayList;
import java.util.List;

public record RecommendationDecisionProfile(
        RecommendationDecisionMode mode,
        List<RecommendationDecisionFactor> appliedFactors,
        int groupMemberEvidenceCount) {

    public RecommendationDecisionProfile {
        appliedFactors = List.copyOf(appliedFactors);
        if (groupMemberEvidenceCount < 0) {
            throw new IllegalArgumentException("groupMemberEvidenceCount must not be negative");
        }
    }

    public static RecommendationDecisionProfile from(
            List<RecommendationDecisionFactor> preferenceFactors,
            boolean groupRequested,
            int groupMemberEvidenceCount) {
        List<RecommendationDecisionFactor> factors = new ArrayList<>(preferenceFactors);
        if (groupRequested && groupMemberEvidenceCount > 0
                && !factors.contains(RecommendationDecisionFactor.GROUP_MEMBER_RECORDS)) {
            factors.add(RecommendationDecisionFactor.GROUP_MEMBER_RECORDS);
        }
        RecommendationDecisionMode mode = groupRequested && groupMemberEvidenceCount > 0
                ? RecommendationDecisionMode.GROUP_GUIDED
                : factors.isEmpty() ? RecommendationDecisionMode.DEFAULT : RecommendationDecisionMode.CONSTRAINT_FOCUSED;
        return new RecommendationDecisionProfile(mode, factors, groupMemberEvidenceCount);
    }
}
