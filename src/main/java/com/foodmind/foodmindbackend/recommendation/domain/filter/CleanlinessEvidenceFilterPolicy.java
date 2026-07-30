package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CleanlinessEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class CleanlinessEvidenceFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        BigDecimal threshold = request.minimumCleanlinessEvidenceScore() == null
                ? preferences.minimumCleanlinessEvidenceScore()
                : request.minimumCleanlinessEvidenceScore();
        if (threshold == null) {
            return FilterDecision.allow();
        }
        CleanlinessEvidence cleanliness = candidate.cleanliness();
        return cleanliness != null && cleanliness.score().compareTo(threshold) >= 0
                ? FilterDecision.allow()
                : FilterDecision.reject(FilterCode.CLEANLINESS_EVIDENCE);
    }
}
