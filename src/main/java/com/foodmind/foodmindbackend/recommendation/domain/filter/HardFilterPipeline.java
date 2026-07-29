package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class HardFilterPipeline {

    private final List<HardFilterPolicy> policies = List.of(
            new AllergenFilterPolicy(),
            new RequiredDietaryTagFilterPolicy(),
            new BudgetCurrencyFilterPolicy(),
            new SpiceFilterPolicy(),
            new DislikedCuisineFilterPolicy(),
            new RecentRepeatFilterPolicy(),
            new AreaDistanceFilterPolicy(),
            new RequestedTimeAvailabilityFilterPolicy(),
            new CleanlinessEvidenceFilterPolicy());

    public EvaluatedCandidate evaluate(
            RecommendationRequestContext request,
            PreferenceEvidence preferences,
            CandidateEvidence candidate) {
        for (HardFilterPolicy policy : policies) {
            FilterDecision decision = policy.apply(request, preferences, candidate);
            if (!decision.allowed()) {
                return new EvaluatedCandidate(candidate, decision.code());
            }
        }
        return new EvaluatedCandidate(candidate, null);
    }
}
