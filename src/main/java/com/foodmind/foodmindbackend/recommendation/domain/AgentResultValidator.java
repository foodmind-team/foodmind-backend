package com.foodmind.foodmindbackend.recommendation.domain;

import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentCandidateResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.foodmind.foodmindbackend.recommendation.domain.agent.ValidatedAgentCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.agent.ValidatedAgentResult;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

public class AgentResultValidator {

    public static final String SUPPORTED_CONTRACT_VERSION = "recommendation-agent-v1";
    public static final String FEATURE_SCHEMA_VERSION = "recommendation-features-v1";
    private static final int MAX_RETURNED_CANDIDATES = 3;
    private static final int MAX_REASON_CODES = 3;
    private static final int MAX_EXPLANATION_LENGTH = 240;
    private static final EnumSet<ReasonCode> ALLOWED_REASON_CODES = EnumSet.allOf(ReasonCode.class);

    public ValidatedAgentResult validate(RecommendationAgentCommand command, AgentGenerationResult result) {
        if (!result.successful()) {
            validateFailureEnvelope(command, result);
            throw new AgentValidationException(result.failureCode());
        }
        require(command.contractVersion().equals(result.contractVersion()), AgentFailureCode.UNSUPPORTED_VERSION);
        require(SUPPORTED_CONTRACT_VERSION.equals(result.contractVersion()), AgentFailureCode.UNSUPPORTED_VERSION);
        require(command.requestId().equals(result.requestId()), AgentFailureCode.SCHEMA_MISMATCH);
        require(command.sessionId().equals(result.sessionId()), AgentFailureCode.SCHEMA_MISMATCH);
        require(command.traceId().equals(result.traceId()), AgentFailureCode.SCHEMA_MISMATCH);
        require("SUCCEEDED".equals(result.modelStatus()), AgentFailureCode.INFERENCE_UNAVAILABLE);
        require(nonBlank(result.modelVersion(), 80), AgentFailureCode.SCHEMA_MISMATCH);
        require(nonBlank(result.agentTraceId(), 128), AgentFailureCode.SCHEMA_MISMATCH);
        require(FEATURE_SCHEMA_VERSION.equals(result.featureSchemaVersion()), AgentFailureCode.UNSUPPORTED_VERSION);

        Map<UUID, RecommendationAgentCandidate> eligibleCandidates = command.candidates().stream()
                .collect(Collectors.toMap(RecommendationAgentCandidate::candidateId, Function.identity()));
        List<AgentCandidateResult> candidates = result.candidates();
        require(candidates.size() <= MAX_RETURNED_CANDIDATES, AgentFailureCode.SCHEMA_MISMATCH);

        Set<UUID> seenIds = new HashSet<>();
        Set<Integer> seenRanks = new HashSet<>();
        Set<RecommendationType> seenTypes = new HashSet<>();
        for (AgentCandidateResult candidate : candidates) {
            require(candidate.candidateId() != null, AgentFailureCode.UNKNOWN_ID);
            require(eligibleCandidates.containsKey(candidate.candidateId()), AgentFailureCode.UNKNOWN_ID);
            require(seenIds.add(candidate.candidateId()), AgentFailureCode.UNKNOWN_ID);
            require(candidate.rank() >= 1 && candidate.rank() <= MAX_RETURNED_CANDIDATES, AgentFailureCode.SCHEMA_MISMATCH);
            require(seenRanks.add(candidate.rank()), AgentFailureCode.SCHEMA_MISMATCH);
            require(candidate.recommendationType() != null, AgentFailureCode.SCHEMA_MISMATCH);
            seenTypes.add(candidate.recommendationType());
            require(candidate.modelScore() != null
                    && candidate.modelScore().compareTo(BigDecimal.ZERO) >= 0
                    && candidate.modelScore().compareTo(BigDecimal.ONE) <= 0, AgentFailureCode.SCHEMA_MISMATCH);
            require(candidate.reasonCodes().size() > 0
                    && candidate.reasonCodes().size() <= MAX_REASON_CODES, AgentFailureCode.INVALID_REASON);
            validateReasons(eligibleCandidates.get(candidate.candidateId()).evidence(), candidate);
            validateExplanation(candidate.explanation());
        }
        for (int rank = 1; rank <= candidates.size(); rank++) {
            require(seenRanks.contains(rank), AgentFailureCode.SCHEMA_MISMATCH);
        }
        if (candidates.size() == MAX_RETURNED_CANDIDATES) {
            require(seenTypes.size() == MAX_RETURNED_CANDIDATES, AgentFailureCode.SCHEMA_MISMATCH);
        }
        boolean hasRecordCandidate = eligibleCandidates.values().stream()
                .anyMatch(candidate -> candidate.evidence().sourceType() == CandidateSourceType.FOOD_RECORD);
        if (hasRecordCandidate) {
            require(candidates.stream().anyMatch(candidate -> eligibleCandidates.get(candidate.candidateId())
                    .evidence().sourceType() == CandidateSourceType.FOOD_RECORD), AgentFailureCode.SOURCE_MIX_POLICY);
        }

        return new ValidatedAgentResult(
                result.contractVersion(),
                result.modelVersion(),
                result.modelStatus(),
                result.featureSchemaVersion(),
                result.agentTraceId(),
                candidates.stream()
                        .sorted(Comparator.comparingInt(AgentCandidateResult::rank))
                        .map(candidate -> new ValidatedAgentCandidate(
                                candidate.candidateId(),
                                candidate.recommendationType(),
                                candidate.rank(),
                                candidate.modelScore(),
                                candidate.reasonCodes(),
                                candidate.explanation().trim()))
                        .toList());
    }

    private void validateFailureEnvelope(RecommendationAgentCommand command, AgentGenerationResult result) {
        if (result.contractVersion() != null) {
            require(command.contractVersion().equals(result.contractVersion()), AgentFailureCode.UNSUPPORTED_VERSION);
            require(command.requestId().equals(result.requestId()), AgentFailureCode.SCHEMA_MISMATCH);
            require(command.sessionId().equals(result.sessionId()), AgentFailureCode.SCHEMA_MISMATCH);
            require(command.traceId().equals(result.traceId()), AgentFailureCode.SCHEMA_MISMATCH);
        }
        if (result.agentTraceId() != null) {
            require(nonBlank(result.agentTraceId(), 128), AgentFailureCode.SCHEMA_MISMATCH);
        }
    }

    private void validateReasons(CandidateEvidence evidence, AgentCandidateResult candidate) {
        Set<ReasonCode> seen = new HashSet<>();
        for (ReasonCode reasonCode : candidate.reasonCodes()) {
            require(reasonCode != null && ALLOWED_REASON_CODES.contains(reasonCode), AgentFailureCode.INVALID_REASON);
            require(seen.add(reasonCode), AgentFailureCode.INVALID_REASON);
            require(reasonSupported(evidence, reasonCode), AgentFailureCode.INVALID_REASON);
        }
    }

    private boolean reasonSupported(CandidateEvidence evidence, ReasonCode reasonCode) {
        return switch (reasonCode) {
            case CUISINE_MATCH -> evidence.cuisineCode() != null;
            case WITHIN_BUDGET -> evidence.price() != null;
            case SPICE_MATCH -> evidence.spiceLevel() != null;
            case NEARBY -> evidence.distanceKm() != null;
            case NOT_RECENTLY_REPEATED -> true;
            case SIMILAR_USERS_LIKED -> evidence.groupRecordCount() > 0;
            case SIMILAR_TO_LIKED_MEALS -> evidence.personalRecordCount() > 0;
            case TRUSTED_GROUP_RATING -> evidence.groupRecordCount() > 0;
            case WANT_TO_TRY -> evidence.wantToTry();
        };
    }

    private void validateExplanation(String explanation) {
        require(nonBlank(explanation, MAX_EXPLANATION_LENGTH), AgentFailureCode.SCHEMA_MISMATCH);
        String lower = explanation.toLowerCase(Locale.ROOT);
        require(!lower.contains("guaranteed")
                && !lower.contains("allergen-free")
                && !lower.contains("allergy-safe")
                && !lower.contains("medical"), AgentFailureCode.SCHEMA_MISMATCH);
    }

    private boolean nonBlank(String value, int maxLength) {
        return value != null && !value.trim().isEmpty() && value.length() <= maxLength;
    }

    private void require(boolean valid, AgentFailureCode failureCode) {
        if (!valid) {
            throw new AgentValidationException(failureCode);
        }
    }

    public static class AgentValidationException extends RuntimeException {

        private final AgentFailureCode failureCode;

        public AgentValidationException(AgentFailureCode failureCode) {
            super(failureCode.name());
            this.failureCode = failureCode;
        }

        public AgentFailureCode failureCode() {
            return failureCode;
        }
    }
}
