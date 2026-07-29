package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class DislikedCuisineFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        return preferences.dislikedCuisineCodes().contains(candidate.cuisineCode())
                ? FilterDecision.reject(FilterCode.DISLIKED_CUISINE)
                : FilterDecision.allow();
    }
}
