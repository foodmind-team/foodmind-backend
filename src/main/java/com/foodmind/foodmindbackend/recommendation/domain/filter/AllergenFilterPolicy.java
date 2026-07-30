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

public class AllergenFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        Set<String> blocked = new HashSet<>(preferences.allergenCodes());
        blocked.addAll(request.avoidAllergenCodes());
        boolean overlaps = candidate.allergenCodes().stream().anyMatch(blocked::contains);
        return overlaps ? FilterDecision.reject(FilterCode.ALLERGEN) : FilterDecision.allow();
    }
}
