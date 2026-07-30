package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.time.OffsetDateTime;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class RecentRepeatFilterPolicy implements HardFilterPolicy {

    private static final int RECENT_REPEAT_DAYS = 14;

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        OffsetDateTime lastSeen = candidate.lastPersonalRecordAt();
        if (lastSeen == null) {
            return FilterDecision.allow();
        }
        OffsetDateTime requestedAt = request.requestedFor() == null ? OffsetDateTime.now() : request.requestedFor();
        return lastSeen.isAfter(requestedAt.minusDays(RECENT_REPEAT_DAYS))
                ? FilterDecision.reject(FilterCode.RECENT_REPEAT)
                : FilterDecision.allow();
    }
}
