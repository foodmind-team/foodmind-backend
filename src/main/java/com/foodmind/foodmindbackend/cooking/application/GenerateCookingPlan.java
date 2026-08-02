package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyRecord;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.application.port.CookingContextQuery;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanInput;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates cooking-plan generation against the agent native contract:
 * assemble request -> idempotency -> PROCESSING root -> invoke agent outside any
 * transaction -> persist one of the four terminal states -> read back.
 */
@Service
public class GenerateCookingPlan {

    private static final String OPERATION = "COOKING_PLAN_GENERATE";

    private final CookingContextQuery contextQuery;
    private final CookingPlanRepository planRepository;
    private final CookingAgentPort cookingAgentPort;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CookingAgentRequestAssembler assembler;
    private final CookingPlanResultValidator validator;

    public GenerateCookingPlan(
            CookingContextQuery contextQuery,
            CookingPlanRepository planRepository,
            CookingAgentPort cookingAgentPort,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            CookingAgentRequestAssembler assembler,
            CookingPlanResultValidator validator) {
        this.contextQuery = contextQuery;
        this.planRepository = planRepository;
        this.cookingAgentPort = cookingAgentPort;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.assembler = assembler;
        this.validator = validator;
    }

    public CookingPlanResult handle(UUID userId, CookingPlanRequestContext request, String idempotencyKey) {
        CookingPreferenceRules mergedRules = mergedRules(contextQuery.preferenceRules(userId), request);
        List<RecipeCandidate> candidates = contextQuery.controlledCandidates(userId, request, mergedRules);
        if (candidates.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Select at least one saved recipe.");
        }
        String traceId = traceId();
        AgentGeneratePlanRequest agentRequest = assembler.assemble(userId, request, mergedRules, candidates, traceId);
        // Hash a stable snapshot of the PUBLIC request (never the agent request, which
        // embeds a per-call trace id) so retries with the same key reproduce the same hash.
        String requestHash = idempotencyService.sha256Hex(toJson(requestSnapshot(request, mergedRules)));
        IdempotencyRecord idempotency = idempotencyService.begin(userId, OPERATION, idempotencyKey, requestHash);

        if ("COMPLETED".equals(idempotency.state()) && idempotency.resourceId() != null) {
            return planRepository.findOwned(userId, idempotency.resourceId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        if (!"IN_PROGRESS".equals(idempotency.state())) {
            throw new ApiException(ErrorCode.CONFLICT, "The idempotency record is not available for retry.");
        }

        UUID planId = planRepository.createProcessing(userId, agentRequest,
                assembler.recipeInputs(candidates, request.servings()), traceId, toJson(agentRequest));

        CookingAgentResult agentResult = invokeAgentOutsideTransaction(agentRequest);
        if (!agentResult.successful()) {
            planRepository.completeFailed(userId, planId, failureCode(agentResult), failedResponse(agentResult),
                    agentResult.rawResponseJson());
        } else {
            try {
                validator.validate(agentResult.response());
                switch (agentResult.response().status()) {
                    case "READY" -> planRepository.completeReady(userId, planId,
                            (AgentReadyPlanResponse) agentResult.response(), agentResult.rawResponseJson());
                    case "NEEDS_CONFIRMATION" -> planRepository.completeConfirmation(userId, planId,
                            (AgentConfirmationPlanResponse) agentResult.response(), agentResult.rawResponseJson());
                    case "INFEASIBLE" -> planRepository.completeInfeasible(userId, planId,
                            (AgentInfeasiblePlanResponse) agentResult.response(), agentResult.rawResponseJson());
                    default -> throw new IllegalStateException("Unreachable status");
                }
            } catch (IllegalArgumentException validationException) {
                planRepository.completeFailed(userId, planId, CookingAgentFailureCode.SCHEMA_MISMATCH,
                        null, agentResult.rawResponseJson());
            }
        }

        CookingPlanResult result = planRepository.findOwned(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        idempotencyService.complete(idempotency.id(), planId, 201, toJson(result));
        return result;
    }

    private CookingAgentResult invokeAgentOutsideTransaction(AgentGeneratePlanRequest agentRequest) {
        Timer.Sample sample = Timer.start(meterRegistry);
        CookingAgentResult result = cookingAgentPort.generate(agentRequest);
        String failureCode = result.successful() ? "NONE" : result.failureCode().name();
        sample.stop(Timer.builder("foodmind.cooking.agent.latency")
                .tag("failure", failureCode)
                .register(meterRegistry));
        if (!result.successful()) {
            meterRegistry.counter("foodmind.cooking.agent.failure", "code", failureCode).increment();
        }
        return result;
    }

    private CookingAgentFailureCode failureCode(CookingAgentResult result) {
        return result.failureCode() == null
                ? CookingAgentFailureCode.AGENT_INTERNAL_ERROR
                : result.failureCode();
    }

    private AgentFailedPlanResponse failedResponse(CookingAgentResult result) {
        return result.response() instanceof AgentFailedPlanResponse failed ? failed : null;
    }

    private CookingPreferenceRules mergedRules(CookingPreferenceRules hardRules, CookingPlanRequestContext request) {
        TreeSet<String> dietary = new TreeSet<>(hardRules.requiredDietaryTagCodes());
        dietary.addAll(request.requiredDietaryTagCodes());
        TreeSet<String> allergens = new TreeSet<>(hardRules.avoidAllergenCodes());
        allergens.addAll(request.avoidAllergenCodes());
        return new CookingPreferenceRules(List.copyOf(dietary), List.copyOf(allergens));
    }

    private Map<String, Object> requestSnapshot(CookingPlanRequestContext request, CookingPreferenceRules mergedRules) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ingredients", request.ingredients().stream().map(this::inputSnapshot).toList());
        snapshot.put("recipeIds", request.recipeIds());
        snapshot.put("servings", request.servings());
        snapshot.put("maxMinutes", request.maxMinutes());
        snapshot.put("maxBudget", request.maxBudget());
        snapshot.put("currency", request.currency());
        snapshot.put("servingAt", request.servingAt());
        snapshot.put("region", request.region());
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("requiredDietaryTagCodes", mergedRules.requiredDietaryTagCodes());
        constraints.put("avoidAllergenCodes", mergedRules.avoidAllergenCodes());
        snapshot.put("constraints", constraints);
        return snapshot;
    }

    private Map<String, Object> inputSnapshot(CookingPlanInput input) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ingredientName", input.ingredientName());
        snapshot.put("quantity", input.quantity());
        snapshot.put("unit", input.unit());
        snapshot.put("source", input.source().name());
        return snapshot;
    }

    private String traceId() {
        String current = CorrelationIdFilter.currentCorrelationId();
        return current == null ? UUID.randomUUID().toString() : current;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise cooking payload.", exception);
        }
    }
}
