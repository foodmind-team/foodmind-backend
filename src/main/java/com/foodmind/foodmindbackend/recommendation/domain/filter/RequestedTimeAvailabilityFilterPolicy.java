package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CandidateSourceType;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class RequestedTimeAvailabilityFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        // A historical record confirms that the user can revisit a meal, not that a venue is
        // currently open. There is no availability constraint in the public request yet, so it
        // remains eligible and is explicitly labelled as historical by clients.
        return candidate.sourceType() == CandidateSourceType.FOOD_RECORD || candidate.available()
                ? FilterDecision.allow()
                : FilterDecision.reject(FilterCode.REQUESTED_TIME_AVAILABILITY);
    }
}
