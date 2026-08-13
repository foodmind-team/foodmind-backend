package com.foodmind.foodmindbackend.recommendation.api.response;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationCandidateResult;
import java.math.BigDecimal;
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
        String candidateSourceType,
        UUID placeMealId,
        UUID foodRecordId,
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
        String explanation,
        BigDecimal modelScore,
        String recordOwnerDisplayName,
        java.time.OffsetDateTime recordOccurredAt,
        String priceKind) {

    public static RecommendationCandidateResponse from(RecommendationCandidateResult candidate) {
        List<String> codes = candidate.reasonCodes().stream().map(Enum::name).toList();
        return new RecommendationCandidateResponse(
                candidate.candidateId(),
                candidate.candidateSourceType().name(),
                candidate.placeMealId(),
                candidate.foodRecordId(),
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
                candidate.explanation(),
                candidate.modelScore(),
                candidate.recordOwnerDisplayName(),
                candidate.recordOccurredAt(),
                candidate.historicalPrice() ? "LAST_RECORDED" : "CURRENT");
    }
}
