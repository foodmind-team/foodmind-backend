package com.foodmind.foodmindbackend.recommendation.application;

import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationSessionRepository;
import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.foodmind.foodmindbackend.recommendation.domain.agent.ValidatedAgentResult;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.SelectedCandidate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

@Service
public class RecommendationTransactionService {

    private static final String AGENT_CONTRACT_VERSION = "recommendation-agent-v1";
    private static final String FEATURE_SCHEMA_VERSION = "recommendation-features-v1";
    private static final long DEFAULT_DEADLINE_SECONDS = 60L;

    private final RecommendationSessionRepository sessionRepository;
    private final Clock clock;

    public RecommendationTransactionService(RecommendationSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional
    public RecommendationAgentCommand createProcessingSession(
            UUID userId,
            RecommendationRequestContext request,
            Map<String, Object> requestSnapshot,
            PreferenceEvidence preferences,
            List<EvaluatedCandidate> evaluatedCandidates,
            String traceId,
            UUID correlationId) {
        UUID sessionId = sessionRepository.createSession(userId, request, requestSnapshot, correlationId);
        Map<String, UUID> candidateIdsBySource = sessionRepository.insertEvaluations(
                sessionId,
                evaluatedCandidates,
                FEATURE_SCHEMA_VERSION);
        sessionRepository.markProcessing(sessionId);
        return new RecommendationAgentCommand(
                AGENT_CONTRACT_VERSION,
                UUID.randomUUID(),
                sessionId,
                traceId,
                OffsetDateTime.now(clock).plusSeconds(DEFAULT_DEADLINE_SECONDS),
                requestSnapshot,
                preferenceSnapshot(preferences),
                evaluatedCandidates.stream()
                        .filter(EvaluatedCandidate::eligible)
                        .map(candidate -> toAgentCandidate(candidate, candidateIdsBySource))
                        .toList());
    }

    @Transactional
    public void completeFromAgent(UUID userId, UUID sessionId, ValidatedAgentResult result) {
        sessionRepository.completeAgent(userId, sessionId, result);
    }

    @Transactional
    public void completeWithFallback(
            UUID userId,
            UUID sessionId,
            List<SelectedCandidate> selectedCandidates,
            AgentFailureCode failureCode,
            String agentContractVersion,
            String agentTraceId) {
        sessionRepository.completeFallback(
                userId,
                sessionId,
                selectedCandidates,
                failureCode,
                agentContractVersion,
                agentTraceId);
    }

    private RecommendationAgentCandidate toAgentCandidate(
            EvaluatedCandidate candidate,
            Map<String, UUID> candidateIdsBySource) {
        UUID candidateId = candidateIdsBySource.get(candidate.evidence().sourceKey());
        return new RecommendationAgentCandidate(
                candidateId,
                candidate.evidence().sourceKey(),
                candidate.evidence(),
                featureSnapshot(candidate.evidence()));
    }

    private Map<String, Object> preferenceSnapshot(PreferenceEvidence preferences) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("budgetMax", preferences.budgetMax());
        snapshot.put("currency", preferences.currency());
        snapshot.put("spiceTolerance", preferences.spiceTolerance());
        snapshot.put("preferredArea", preferences.preferredArea());
        snapshot.put("preferredLatitude", preferences.preferredLatitude());
        snapshot.put("preferredLongitude", preferences.preferredLongitude());
        snapshot.put("maxDistanceKm", preferences.maxDistanceKm());
        snapshot.put("minimumCleanlinessEvidenceScore", preferences.minimumCleanlinessEvidenceScore());
        snapshot.put("likedCuisineCodes", preferences.likedCuisineCodes());
        snapshot.put("dislikedCuisineCodes", preferences.dislikedCuisineCodes());
        snapshot.put("dietaryTagCodes", preferences.dietaryTagCodes());
        snapshot.put("allergenCodes", preferences.allergenCodes());
        snapshot.put("preferredMealTypes", preferences.preferredMealTypes());
        return snapshot;
    }

    private Map<String, Object> featureSnapshot(CandidateEvidence evidence) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mealType", evidence.mealType());
        snapshot.put("cuisineCode", evidence.cuisineCode());
        snapshot.put("area", evidence.area());
        snapshot.put("priceAmount", amount(evidence));
        snapshot.put("currency", evidence.price() == null ? null : evidence.price().currency());
        snapshot.put("spiceLevel", evidence.spiceLevel());
        snapshot.put("available", evidence.available());
        snapshot.put("cleanlinessScore", evidence.cleanliness() == null ? null : evidence.cleanliness().score());
        snapshot.put("dietaryTagCodes", evidence.dietaryTagCodes());
        snapshot.put("allergenCodes", evidence.allergenCodes());
        snapshot.put("wantToTry", evidence.wantToTry());
        snapshot.put("personalRecordCount", evidence.personalRecordCount());
        snapshot.put("personalAverageRating", evidence.personalAverageRating());
        snapshot.put("groupRecordCount", evidence.groupRecordCount());
        snapshot.put("groupAverageRating", evidence.groupAverageRating());
        snapshot.put("distanceKm", evidence.distanceKm());
        return snapshot;
    }

    private BigDecimal amount(CandidateEvidence evidence) {
        return evidence.price() == null ? null : evidence.price().amount();
    }
}
