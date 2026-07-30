package com.foodmind.foodmindbackend.recommendation.api.response;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationCandidateResult;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationCandidateResponse(
        UUID candidateId,
        UUID placeMealId,
        UUID mealId,
        String mealName,
        UUID placeId,
        String placeName,
        String area,
        RecommendationMoneyResponse price,
        String recommendationType,
        int rank,
        List<String> reasonCodes,
        List<String> reasons,
        String explanation) {

    public static RecommendationCandidateResponse from(RecommendationCandidateResult candidate) {
        List<String> codes = candidate.reasonCodes().stream().map(Enum::name).toList();
        return new RecommendationCandidateResponse(
                candidate.candidateId(),
                candidate.placeMealId(),
                candidate.mealId(),
                candidate.mealName(),
                candidate.placeId(),
                candidate.placeName(),
                candidate.area(),
                RecommendationMoneyResponse.from(candidate.price()),
                candidate.recommendationType().name(),
                candidate.rank(),
                codes,
                codes,
                candidate.explanation());
    }
}
