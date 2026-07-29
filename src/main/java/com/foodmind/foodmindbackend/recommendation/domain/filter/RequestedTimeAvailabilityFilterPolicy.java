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

public class RequestedTimeAvailabilityFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        return candidate.available()
                ? FilterDecision.allow()
                : FilterDecision.reject(FilterCode.REQUESTED_TIME_AVAILABILITY);
    }
}
