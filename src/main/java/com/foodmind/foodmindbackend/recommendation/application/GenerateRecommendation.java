package com.foodmind.foodmindbackend.recommendation.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyRecord;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationContextQuery;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationAgentPort;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationSessionRepository;
import com.foodmind.foodmindbackend.recommendation.domain.AgentResultValidator;
import com.foodmind.foodmindbackend.recommendation.domain.AgentResultValidator.AgentValidationException;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.foodmind.foodmindbackend.recommendation.domain.agent.ValidatedAgentResult;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.FallbackSelector;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.SelectedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.filter.HardFilterPipeline;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

@Service
public class GenerateRecommendation {

    private static final String OPERATION = "RECOMMENDATION_GENERATE";

    private final RecommendationContextQuery contextQuery;
    private final RecommendationTransactionService transactionService;
    private final RecommendationSessionRepository sessionRepository;
    private final RecommendationAgentPort recommendationAgentPort;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final SubmitFeedback submitFeedback;
    private final HardFilterPipeline filterPipeline = new HardFilterPipeline();
    private final FallbackSelector fallbackSelector = new FallbackSelector();
    private final AgentResultValidator agentResultValidator = new AgentResultValidator();

    public GenerateRecommendation(
            RecommendationContextQuery contextQuery,
            RecommendationTransactionService transactionService,
            RecommendationSessionRepository sessionRepository,
            RecommendationAgentPort recommendationAgentPort,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            SubmitFeedback submitFeedback) {
        this.contextQuery = contextQuery;
        this.transactionService = transactionService;
        this.sessionRepository = sessionRepository;
        this.recommendationAgentPort = recommendationAgentPort;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.submitFeedback = submitFeedback;
    }

    public RecommendationResult handle(UUID userId, RecommendationRequestContext request, String idempotencyKey) {
        validateGroup(userId, request);
        validateParentSession(userId, request);
        Map<String, Object> requestSnapshot = requestSnapshot(request);
        String canonicalRequest = toJson(requestSnapshot);
        String requestHash = idempotencyService.sha256Hex(canonicalRequest);
        IdempotencyRecord idempotency = idempotencyService.begin(userId, OPERATION, idempotencyKey, requestHash);
        String traceId = traceId();

        if ("COMPLETED".equals(idempotency.state()) && idempotency.resourceId() != null) {
            return sessionRepository.findResult(userId, idempotency.resourceId(), traceId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        if (!"IN_PROGRESS".equals(idempotency.state())) {
            throw new ApiException(ErrorCode.CONFLICT, "The idempotency record is not available for retry.");
        }

        RecommendationContext context = contextQuery.load(userId, request);
        List<EvaluatedCandidate> evaluatedCandidates = context.candidates().stream()
                .map(candidate -> filterPipeline.evaluate(request, context.preferences(), candidate))
                .toList();
        List<SelectedCandidate> selectedCandidates = fallbackSelector.select(evaluatedCandidates, request, context.preferences());

        RecommendationAgentCommand command = transactionService.createProcessingSession(
                userId,
                request,
                requestSnapshot,
                context.preferences(),
                evaluatedCandidates,
                traceId,
                correlationUuid(traceId));

        AgentGenerationResult agentResult = command.candidates().isEmpty()
                ? AgentGenerationResult.failure(
                        AgentFailureCode.AGENT_DISABLED,
                        null,
                        command.requestId(),
                        command.sessionId(),
                        command.traceId(),
                        null)
                : invokeAgentOutsideTransaction(command);
        AgentFailureCode fallbackFailureCode = null;
        String agentContractVersion = null;
        String agentTraceId = null;
        try {
            ValidatedAgentResult validated = agentResultValidator.validate(command, agentResult);
            transactionService.completeFromAgent(userId, command.sessionId(), validated);
        } catch (AgentValidationException exception) {
            fallbackFailureCode = exception.failureCode();
            agentContractVersion = fallbackContractVersion(agentResult);
            agentTraceId = agentResult.agentTraceId();
            recordSchemaRejection(fallbackFailureCode);
            transactionService.completeWithFallback(
                    userId,
                    command.sessionId(),
                    selectedCandidates,
                    fallbackFailureCode,
                    agentContractVersion,
                    agentTraceId);
        }

        RecommendationResult result = sessionRepository.findResult(userId, command.sessionId(), traceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        idempotencyService.complete(idempotency.id(), command.sessionId(), 201, toJson(result));
        recordFallbackRate(result, fallbackFailureCode);
        return result;
    }

    public AgentGenerationResult invokeAgentOutsideTransaction(RecommendationAgentCommand command) {
        Timer.Sample sample = Timer.start(meterRegistry);
        AgentGenerationResult result = recommendationAgentPort.generate(command);
        String failureCode = result.successful() ? "NONE" : result.failureCode().name();
        String modelVersion = result.modelVersion() == null ? "none" : result.modelVersion();
        sample.stop(Timer.builder("foodmind.recommendation.agent.latency")
                .tag("failure", failureCode)
                .tag("modelVersion", modelVersion)
                .register(meterRegistry));
        if (!result.successful()) {
            meterRegistry.counter("foodmind.recommendation.agent.failure", "code", failureCode).increment();
        }
        return result;
    }

    private void validateGroup(UUID userId, RecommendationRequestContext request) {
        if (request.groupId() != null && !contextQuery.activeGroupMember(userId, request.groupId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Map<String, Object> requestSnapshot(RecommendationRequestContext request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractVersion", "recommendation-public-v1");
        snapshot.put("parentSessionId", request.parentSessionId());
        snapshot.put("groupId", request.groupId());
        snapshot.put("mealType", request.mealType());
        snapshot.put("maxBudget", request.maxBudget());
        snapshot.put("currency", request.currency());
        snapshot.put("area", request.area());
        snapshot.put("latitude", request.latitude());
        snapshot.put("longitude", request.longitude());
        snapshot.put("maxDistanceKm", request.maxDistanceKm());
        snapshot.put("mood", request.mood());
        snapshot.put("requestedFor", request.requestedFor());
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("avoidAllergenCodes", request.avoidAllergenCodes());
        constraints.put("requiredDietaryTagCodes", request.requiredDietaryTagCodes());
        constraints.put("maxSpiceLevel", request.maxSpiceLevel());
        constraints.put("minimumCleanlinessEvidenceScore", request.minimumCleanlinessEvidenceScore());
        snapshot.put("constraints", constraints);
        return snapshot;
    }

    private String traceId() {
        String current = CorrelationIdFilter.currentCorrelationId();
        return current == null ? UUID.randomUUID().toString() : current;
    }

    private UUID correlationUuid(String traceId) {
        try {
            return UUID.fromString(traceId);
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID();
        }
    }

    private void validateParentSession(UUID userId, RecommendationRequestContext request) {
        if (request.parentSessionId() != null) {
            submitFeedback.linkReRecommendation(userId, request.parentSessionId());
        }
    }

    private String fallbackContractVersion(AgentGenerationResult agentResult) {
        if (agentResult.contractVersion() == null || agentResult.contractVersion().isBlank()) {
            return null;
        }
        return agentResult.contractVersion();
    }

    private void recordSchemaRejection(AgentFailureCode failureCode) {
        if (failureCode == AgentFailureCode.SCHEMA_MISMATCH
                || failureCode == AgentFailureCode.UNKNOWN_ID
                || failureCode == AgentFailureCode.INVALID_REASON
                || failureCode == AgentFailureCode.UNSUPPORTED_VERSION) {
            meterRegistry.counter("foodmind.recommendation.agent.schema.rejection", "code", failureCode.name()).increment();
        }
    }

    private void recordFallbackRate(RecommendationResult result, AgentFailureCode failureCode) {
        if ("FALLBACK_SUCCEEDED".equals(result.status()) || "NO_VALID_CANDIDATE".equals(result.status())) {
            meterRegistry.counter(
                    "foodmind.recommendation.fallback",
                    "source",
                    failureCode == null ? "NO_ELIGIBLE_CANDIDATE" : failureCode.name()).increment();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise recommendation payload.", exception);
        }
    }
}
