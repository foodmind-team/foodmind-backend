package com.foodmind.foodmindbackend.recommendation.application;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationSessionRepository;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationResult;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationSessionSummary;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

@Service
public class GetRecommendation {

    private final RecommendationSessionRepository sessionRepository;

    public GetRecommendation(RecommendationSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public RecommendationResult byId(UUID userId, UUID sessionId) {
        return sessionRepository.findResult(userId, sessionId, traceId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<RecommendationSessionSummary> history(UUID userId, int page, int size) {
        return PageResponse.of(
                sessionRepository.history(userId, page, size),
                page,
                size,
                sessionRepository.historyCount(userId));
    }

    private String traceId() {
        String current = CorrelationIdFilter.currentCorrelationId();
        return current == null ? UUID.randomUUID().toString() : current;
    }
}
