package com.foodmind.foodmindbackend.recommendation.domain.reason;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class ReasonTemplateRenderer {

    public List<ReasonCode> reasonCodes(RecommendationType type, CandidateEvidence candidate, boolean cuisineLiked) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (type == RecommendationType.GROUP_INSPIRED && candidate.groupRecordCount() > 0) {
            reasons.add(ReasonCode.TRUSTED_GROUP_RATING);
        }
        if (type == RecommendationType.PERSONAL && candidate.personalRecordCount() > 0) {
            reasons.add(ReasonCode.SIMILAR_TO_LIKED_MEALS);
        }
        if (type == RecommendationType.EXPLORATORY) {
            reasons.add(ReasonCode.SIMILAR_USERS_LIKED);
        }
        if (candidate.wantToTry()) {
            reasons.add(ReasonCode.WANT_TO_TRY);
        }
        if (cuisineLiked) {
            reasons.add(ReasonCode.CUISINE_MATCH);
        }
        if (candidate.price() != null) {
            reasons.add(ReasonCode.WITHIN_BUDGET);
        }
        if (candidate.spiceLevel() != null) {
            reasons.add(ReasonCode.SPICE_MATCH);
        }
        if (candidate.distanceKm() != null) {
            reasons.add(ReasonCode.NEARBY);
        }
        if (candidate.lastPersonalRecordAt() == null) {
            reasons.add(ReasonCode.NOT_RECENTLY_REPEATED);
        }
        return reasons.stream().distinct().limit(3).toList();
    }

    public String explanation(RecommendationType type, CandidateEvidence candidate, List<ReasonCode> reasonCodes) {
        String meal = candidate.mealName();
        String place = candidate.placeName();
        if (reasonCodes.contains(ReasonCode.WANT_TO_TRY)) {
            return "%s at %s is on your Want to Try list and fits the current hard constraints.".formatted(meal, place);
        }
        if (type == RecommendationType.GROUP_INSPIRED && reasonCodes.contains(ReasonCode.TRUSTED_GROUP_RATING)) {
            return "%s at %s is backed by recent active-group meal evidence.".formatted(meal, place);
        }
        if (type == RecommendationType.EXPLORATORY) {
            return "%s at %s adds a safe non-recent option within the current constraints.".formatted(meal, place);
        }
        if (reasonCodes.contains(ReasonCode.CUISINE_MATCH)) {
            return "%s at %s matches cuisines you have explicitly liked.".formatted(meal, place);
        }
        return "%s at %s is an eligible fallback candidate that satisfies the hard rules.".formatted(meal, place);
    }
}
