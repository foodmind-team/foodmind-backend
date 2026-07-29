package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.util.HashSet;
import java.util.Set;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class RequiredDietaryTagFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        Set<String> required = new HashSet<>(preferences.dietaryTagCodes());
        required.addAll(request.requiredDietaryTagCodes());
        return candidate.dietaryTagCodes().containsAll(required)
                ? FilterDecision.allow()
                : FilterDecision.reject(FilterCode.REQUIRED_DIETARY_TAG);
    }
}
