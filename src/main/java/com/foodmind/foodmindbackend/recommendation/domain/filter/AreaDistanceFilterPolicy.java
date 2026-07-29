package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class AreaDistanceFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        BigDecimal maxDistance = request.maxDistanceKm() == null ? preferences.maxDistanceKm() : request.maxDistanceKm();
        if (maxDistance != null) {
            return candidate.distanceKm() != null && candidate.distanceKm().compareTo(maxDistance) <= 0
                    ? FilterDecision.allow()
                    : FilterDecision.reject(FilterCode.AREA_DISTANCE);
        }
        String area = request.area() == null ? preferences.preferredArea() : request.area();
        return area == null || (candidate.area() != null && candidate.area().equalsIgnoreCase(area))
                ? FilterDecision.allow()
                : FilterDecision.reject(FilterCode.AREA_DISTANCE);
    }
}
