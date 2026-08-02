package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyRecord;
import com.foodmind.foodmindbackend.common.idempotency.IdempotencyService;
import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.cooking.api.request.SubmitDecisionsRequest;
import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.application.port.CookingContextQuery;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanInput;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentApprovedDecision;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String OPERATION_DECIDE = "COOKING_PLAN_DECIDE";
    private static final int MAX_TEXT_ANSWER_LENGTH = 500;

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
        String requestHashSeed = toJson(requestSnapshot(request, mergedRules));
        return generateAndPersist(userId, agentRequest,
                assembler.recipeInputs(candidates, request.servings()), traceId,
                OPERATION, idempotencyKey, requestHashSeed);
    }

    /**
     * Submits decisions for a NEEDS_CONFIRMATION plan: guards the plan is still awaiting
     * confirmation, validates the answers against the presented confirmation questions,
     * maps them to approved decisions (rebinding the plan revision), then re-runs the
     * agent generation to produce a new revision plan.
     */
    public CookingPlanResult submitDecisions(
            UUID userId,
            UUID planId,
            List<SubmitDecisionsRequest.QuestionAnswer> answers,
            String idempotencyKey) {
        CookingPlanResult confirmationPlan = planRepository.findOwned(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"NEEDS_CONFIRMATION".equals(confirmationPlan.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "The plan is not awaiting confirmation.");
        }
        String storedRevision = confirmationPlan.planRevision();
        if (storedRevision == null || storedRevision.isBlank()) {
            throw new ApiException(ErrorCode.CONFLICT, "The confirmation plan has no revision.");
        }
        String newRevision = nextRevision(storedRevision);
        String requestContext = planRepository.findRequestContext(userId, planId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        AgentGeneratePlanRequest base = parseRequestContext(requestContext);
        List<AgentApprovedDecision> approvedDecisions =
                mapAnswers(confirmationPlan, answers, newRevision);
        String traceId = traceId();
        AgentGeneratePlanRequest resubmission = new AgentGeneratePlanRequest(
                sanitiseRequestId(traceId),
                base.userId(),
                base.recipes(),
                base.dietaryRestrictions(),
                base.userAllergens(),
                base.timeLimitMinutes(),
                base.cookingDate(),
                base.servingAt(),
                base.servingTime(),
                base.inventoryLots(),
                base.kitchenResources(),
                approvedDecisions,
                base.schemaVersion(),
                newRevision,
                base.region());
        String requestHashSeed = toJson(decideSnapshot(planId, newRevision, answers));
        return generateAndPersist(userId, resubmission, resubmission.recipes(),
                traceId, OPERATION_DECIDE, idempotencyKey, requestHashSeed);
    }

    private CookingPlanResult generateAndPersist(
            UUID userId,
            AgentGeneratePlanRequest agentRequest,
            List<AgentRecipeInput> sources,
            String traceId,
            String operation,
            String idempotencyKey,
            String requestHashSeed) {
        String requestHash = idempotencyService.sha256Hex(requestHashSeed);
        IdempotencyRecord idempotency = idempotencyService.begin(userId, operation, idempotencyKey, requestHash);

        if ("COMPLETED".equals(idempotency.state()) && idempotency.resourceId() != null) {
            return planRepository.findOwned(userId, idempotency.resourceId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        if (!"IN_PROGRESS".equals(idempotency.state())) {
            throw new ApiException(ErrorCode.CONFLICT, "The idempotency record is not available for retry.");
        }

        UUID planId = planRepository.createProcessing(userId, agentRequest, sources, traceId, toJson(agentRequest));

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

    private Map<String, Object> decideSnapshot(
            UUID planId,
            String newRevision,
            List<SubmitDecisionsRequest.QuestionAnswer> answers) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planId", planId);
        snapshot.put("planRevision", newRevision);
        snapshot.put("answers", answers.stream()
                .map(answer -> Map.of("questionId", answer.questionId(), "value", answer.value()))
                .toList());
        return snapshot;
    }

    /**
     * {@code {request_id}:v1} -> {@code {request_id}:v2}. Falls back to appending
     * {@code :v2} when the stored revision is not parseable.
     */
    private String nextRevision(String storedRevision) {
        int marker = storedRevision.lastIndexOf(":v");
        if (marker < 0) {
            return storedRevision + ":v2";
        }
        String prefix = storedRevision.substring(0, marker);
        String number = storedRevision.substring(marker + 2);
        try {
            return prefix + ":v" + (Integer.parseInt(number) + 1);
        } catch (NumberFormatException exception) {
            return storedRevision + ":v2";
        }
    }

    /**
     * Validates client answers against the presented confirmation questions and maps them
     * to approved decisions, mirroring the agent's {@code answers_to_approved_decisions}
     * (repair/options.py): only CHOICE answers selecting a presented decision emit one —
     * looked up verbatim by option_id, with the plan revision rebound to the answered one.
     */
    private List<AgentApprovedDecision> mapAnswers(
            CookingPlanResult confirmationPlan,
            List<SubmitDecisionsRequest.QuestionAnswer> answers,
            String newRevision) {
        Map<String, CookingPlanResult.ConfirmationQuestion> questionsById = new HashMap<>();
        for (CookingPlanResult.ConfirmationQuestion question : confirmationPlan.confirmationQuestions()) {
            questionsById.put(question.questionId(), question);
        }
        List<String> issues = new ArrayList<>();
        Set<String> answeredIds = new HashSet<>();
        for (SubmitDecisionsRequest.QuestionAnswer answer : answers) {
            CookingPlanResult.ConfirmationQuestion question = questionsById.get(answer.questionId());
            if (question == null) {
                issues.add("unknown question_id: " + answer.questionId());
                continue;
            }
            if (!answeredIds.add(answer.questionId())) {
                issues.add("duplicate answer for question_id: " + answer.questionId());
            }
            String value = answer.value().trim();
            if ("CHOICE".equals(question.responseType())) {
                boolean valid = question.options().stream().anyMatch(option -> option.value().equals(value));
                if (!valid) {
                    issues.add("invalid option for question '" + answer.questionId() + "': " + answer.value());
                }
            } else {
                if (value.isEmpty()) {
                    issues.add("empty answer for question '" + answer.questionId() + "'");
                } else if (value.length() > MAX_TEXT_ANSWER_LENGTH) {
                    issues.add("answer for question '" + answer.questionId() + "' exceeds "
                            + MAX_TEXT_ANSWER_LENGTH + " characters");
                }
            }
        }
        for (CookingPlanResult.ConfirmationQuestion question : confirmationPlan.confirmationQuestions()) {
            if (question.required() && !answeredIds.contains(question.questionId())) {
                issues.add("missing required answer for question '" + question.questionId() + "'");
            }
        }
        if (!issues.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, String.join("; ", issues));
        }

        Map<String, CookingPlanResult.Decision> decisionsByOptionId = new HashMap<>();
        for (CookingPlanResult.Decision decision : confirmationPlan.decisions()) {
            decisionsByOptionId.put(decision.optionId(), decision);
        }
        List<AgentApprovedDecision> mapped = new ArrayList<>();
        for (SubmitDecisionsRequest.QuestionAnswer answer : answers) {
            CookingPlanResult.Decision decision = decisionsByOptionId.get(answer.value().trim());
            if (decision != null) {
                mapped.add(new AgentApprovedDecision(
                        decision.optionId(), decision.optionType(), decision.payload(), newRevision));
            }
        }
        return mapped;
    }

    private AgentGeneratePlanRequest parseRequestContext(String requestContext) {
        try {
            return objectMapper.readValue(requestContext, AgentGeneratePlanRequest.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid stored agent request context.", exception);
        }
    }

    /** New request id must be ≤128 chars of [A-Za-z0-9_-] (unique agent_request_id). */
    private String sanitiseRequestId(String traceId) {
        if (traceId == null) {
            return UUID.randomUUID().toString();
        }
        String cleaned = traceId.replaceAll("[^A-Za-z0-9_-]", "-");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > 128 ? cleaned.substring(0, 128) : cleaned;
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
