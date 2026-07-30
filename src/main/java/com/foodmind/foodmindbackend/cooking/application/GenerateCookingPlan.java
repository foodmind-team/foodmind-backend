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
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResultValidator;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResultValidator.CookingAgentValidationException;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentGenerationResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.ValidatedCookingAgentResult;
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
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Service
public class GenerateCookingPlan {

    private static final String OPERATION = "COOKING_PLAN_GENERATE";

    private final CookingContextQuery contextQuery;
    private final CookingTransactionService transactionService;
    private final CookingPlanRepository planRepository;
    private final CookingAgentPort cookingAgentPort;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CookingPlanResultValidator validator = new CookingPlanResultValidator();

    public GenerateCookingPlan(
            CookingContextQuery contextQuery,
            CookingTransactionService transactionService,
            CookingPlanRepository planRepository,
            CookingAgentPort cookingAgentPort,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.contextQuery = contextQuery;
        this.transactionService = transactionService;
        this.planRepository = planRepository;
        this.cookingAgentPort = cookingAgentPort;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public CookingPlanResult handle(UUID userId, CookingPlanRequestContext request, String idempotencyKey) {
        CookingPreferenceRules mergedRules = mergedRules(contextQuery.preferenceRules(userId), request);
        Map<String, Object> requestSnapshot = requestSnapshot(request, mergedRules);
        String requestHash = idempotencyService.sha256Hex(toJson(requestSnapshot));
        IdempotencyRecord idempotency = idempotencyService.begin(userId, OPERATION, idempotencyKey, requestHash);
        String traceId = traceId();

        if ("COMPLETED".equals(idempotency.state()) && idempotency.resourceId() != null) {
            return planRepository.findOwned(userId, idempotency.resourceId(), traceId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        if (!"IN_PROGRESS".equals(idempotency.state())) {
            throw new ApiException(ErrorCode.CONFLICT, "The idempotency record is not available for retry.");
        }

        List<RecipeCandidate> candidates = contextQuery.controlledCandidates(userId, request, mergedRules);
        CookingAgentCommand command = transactionService.createProcessingPlan(
                userId,
                request,
                requestSnapshot,
                preferenceSnapshot(mergedRules),
                candidates,
                traceId,
                correlationUuid(traceId));

        if (command.candidates().isEmpty()) {
            transactionService.markFailed(
                    userId,
                    command.planId(),
                    CookingAgentFailureCode.NO_RECIPE_MATCH,
                    null,
                    null);
        } else {
            CookingAgentGenerationResult agentResult = invokeAgentOutsideTransaction(command);
            try {
                ValidatedCookingAgentResult validated = validator.validate(command, agentResult);
                transactionService.completePlan(userId, command.planId(), validated);
            } catch (CookingAgentValidationException exception) {
                transactionService.markFailed(
                        userId,
                        command.planId(),
                        exception.failureCode(),
                        agentResult.contractVersion(),
                        agentResult.agentTraceId());
            }
        }

        CookingPlanResult result = planRepository.findOwned(userId, command.planId(), traceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        idempotencyService.complete(idempotency.id(), command.planId(), 201, toJson(result));
        return result;
    }

    public CookingAgentGenerationResult invokeAgentOutsideTransaction(CookingAgentCommand command) {
        Timer.Sample sample = Timer.start(meterRegistry);
        CookingAgentGenerationResult result = cookingAgentPort.generate(command);
        String failureCode = result.successful() ? "NONE" : result.failureCode().name();
        sample.stop(Timer.builder("foodmind.cooking.agent.latency")
                .tag("failure", failureCode)
                .register(meterRegistry));
        if (!result.successful()) {
            meterRegistry.counter("foodmind.cooking.agent.failure", "code", failureCode).increment();
        }
        return result;
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
        snapshot.put("contractVersion", "cooking-public-v1");
        snapshot.put("ingredients", request.ingredients().stream().map(this::inputSnapshot).toList());
        snapshot.put("servings", request.servings());
        snapshot.put("maxMinutes", request.maxMinutes());
        snapshot.put("maxBudget", request.maxBudget());
        snapshot.put("currency", request.currency());
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

    private Map<String, Object> preferenceSnapshot(CookingPreferenceRules rules) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requiredDietaryTagCodes", rules.requiredDietaryTagCodes());
        snapshot.put("avoidAllergenCodes", rules.avoidAllergenCodes());
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialise cooking payload.", exception);
        }
    }
}
