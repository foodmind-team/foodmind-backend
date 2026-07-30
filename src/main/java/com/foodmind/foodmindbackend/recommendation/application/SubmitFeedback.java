package com.foodmind.foodmindbackend.recommendation.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyRecord;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationFeedbackRepository;
import com.foodmind.foodmindbackend.recommendation.domain.FeedbackPolicy;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackCommand;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEvent;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackEventType;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationFeedbackTarget;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@Service
public class SubmitFeedback {

    private static final String OPERATION = "RECOMMENDATION_FEEDBACK";

    private final RecommendationFeedbackRepository feedbackRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final FeedbackPolicy feedbackPolicy;

    public SubmitFeedback(
            RecommendationFeedbackRepository feedbackRepository,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.feedbackRepository = feedbackRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.feedbackPolicy = new FeedbackPolicy(clock);
    }

    @Transactional
    public SubmitFeedbackResult handle(UUID userId, RecommendationFeedbackCommand command, String idempotencyKey) {
        String canonicalPayload = toJson(canonicalPayload(command));
        String payloadHash = idempotencyService.sha256Hex(canonicalPayload);
        IdempotencyRecord idempotency = idempotencyService.begin(userId, OPERATION, idempotencyKey, payloadHash);
        if ("COMPLETED".equals(idempotency.state()) && idempotency.resourceId() != null) {
            RecommendationFeedbackEvent event = feedbackRepository.findById(userId, idempotency.resourceId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
            return new SubmitFeedbackResult(event, false, feedbackPolicy.labelFor(event.eventType()));
        }
        if (!"IN_PROGRESS".equals(idempotency.state())) {
            throw new ApiException(ErrorCode.CONFLICT, "The idempotency record is not available for retry.");
        }

        RecommendationFeedbackTarget target = loadOwnerScopedTarget(userId, command);
        feedbackPolicy.validatePayload(command, target);
        validateTerminalDecision(userId, command);
        validateResultingFoodRecord(userId, command, target);

        RecommendationFeedbackEvent event = new RecommendationFeedbackEvent(
                UUID.randomUUID(),
                command.sessionId(),
                command.candidateId(),
                userId,
                command.eventType(),
                command.reasonCode(),
                command.rating(),
                command.booleanValue(),
                command.resultingFoodRecordId(),
                feedbackPolicy.deriveTemporaryConstraint(command),
                idempotencyKey,
                null);
        RecommendationFeedbackEvent inserted = feedbackRepository.insertOrResolveRetry(event, payloadHash);
        idempotencyService.complete(idempotency.id(), inserted.id(), 201, toJson(inserted));
        return new SubmitFeedbackResult(inserted, true, feedbackPolicy.labelFor(inserted.eventType()));
    }

    public UUID linkReRecommendation(UUID userId, UUID parentSessionId) {
        if (!feedbackRepository.sessionOwnedBy(userId, parentSessionId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return parentSessionId;
    }

    private RecommendationFeedbackTarget loadOwnerScopedTarget(UUID userId, RecommendationFeedbackCommand command) {
        if (command.eventType() == RecommendationFeedbackEventType.RERECOMMEND_REQUESTED || command.candidateId() == null) {
            if (!feedbackRepository.sessionOwnedBy(userId, command.sessionId())) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return null;
        }
        return feedbackRepository.findTarget(userId, command.sessionId(), command.candidateId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateTerminalDecision(UUID userId, RecommendationFeedbackCommand command) {
        if (command.eventType() != RecommendationFeedbackEventType.ACCEPTED
                && command.eventType() != RecommendationFeedbackEventType.REJECTED) {
            return;
        }
        feedbackRepository.existingTerminalDecision(userId, command.sessionId(), command.candidateId())
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.CONFLICT, "A terminal decision already exists for this candidate.");
                });
    }

    private void validateResultingFoodRecord(
            UUID userId,
            RecommendationFeedbackCommand command,
            RecommendationFeedbackTarget target) {
        if (command.resultingFoodRecordId() == null) {
            return;
        }
        if (target == null || !feedbackRepository.resultingFoodRecordMatches(
                userId,
                command.resultingFoodRecordId(),
                target.mealId(),
                target.placeId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Map<String, Object> canonicalPayload(RecommendationFeedbackCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", command.sessionId());
        payload.put("candidateId", command.candidateId());
        payload.put("eventType", command.eventType());
        payload.put("reasonCode", command.reasonCode());
        payload.put("rating", command.rating());
        payload.put("booleanValue", command.booleanValue());
        payload.put("resultingFoodRecordId", command.resultingFoodRecordId());
        payload.put("effectiveUntil", command.effectiveUntil());
        return payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise feedback payload.", exception);
        }
    }

    public record SubmitFeedbackResult(
            RecommendationFeedbackEvent event,
            boolean created,
            Integer supervisedLabel) {
    }
}
