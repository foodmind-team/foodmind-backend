package com.foodmind.foodmindbackend.cooking.application;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentCompletionItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationQuestion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDecision;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDishCompletion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentLotAllocation;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentMiseEnPlaceItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPolicySource;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRepairOption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentSafetyPolicy;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTimelineTask;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Projects an {@link AgentPlanResponse} onto the {@link CookingPlanResult} domain
 * aggregate (all sub-lists). The repository overlays storage metadata afterwards.
 */
@Component
public class CookingPlanResultMapper {

    public CookingPlanResult toResult(AgentPlanResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Agent response is null.");
        }
        return switch (response.status()) {
            case "READY" -> ready((AgentReadyPlanResponse) response);
            case "NEEDS_CONFIRMATION" -> confirmation((AgentConfirmationPlanResponse) response);
            case "INFEASIBLE" -> infeasible((AgentInfeasiblePlanResponse) response);
            case "FAILED" -> failed((AgentFailedPlanResponse) response);
            default -> throw new IllegalArgumentException("Unknown response status: " + response.status());
        };
    }

    public CookingPlanResult.SafetyPolicy safetyPolicy(AgentSafetyPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new CookingPlanResult.SafetyPolicy(
                policy.region(),
                policy.version(),
                policy.effectiveAt(),
                policy.sources().stream().map(this::policySource).toList());
    }

    private CookingPlanResult ready(AgentReadyPlanResponse response) {
        return new CookingPlanResult(
                uuidOrNull(response.planId()),
                response.status(),
                null, null, null, null, null,
                response.solverStatus(),
                response.makespanMinutes(),
                null, null, null, null, null, null,
                List.of(),
                response.timeline().stream().map(this::timelineTask).toList(),
                response.miseEnPlace().stream().map(this::miseEnPlace).toList(),
                response.dishCompletions().stream().map(this::dishCompletion).toList(),
                response.completionChecklist().stream().map(this::completionItem).toList(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                safetyPolicy(response.safetyPolicy()),
                response.explanation(),
                response.explanationSource());
    }

    private CookingPlanResult confirmation(AgentConfirmationPlanResponse response) {
        return new CookingPlanResult(
                uuidOrNull(response.planId()),
                response.status(),
                response.planRevision(),
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                response.assumptions().stream().map(this::assumption).toList(),
                response.repairOptions().stream().map(this::repairOption).toList(),
                response.questions(),
                confirmationQuestions(response),
                decisions(response.decisions()),
                List.of(), List.of(),
                safetyPolicy(response.safetyPolicy()),
                null, null);
    }

    private CookingPlanResult infeasible(AgentInfeasiblePlanResponse response) {
        return new CookingPlanResult(
                uuidOrNull(response.planId()),
                response.status(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                response.reasons(),
                response.safeAlternatives(),
                null, null, null);
    }

    private CookingPlanResult failed(AgentFailedPlanResponse response) {
        return new CookingPlanResult(
                null,
                response.status(),
                null, null, null, null, null, null, null,
                null, null,
                response.errorCode(),
                response.message(),
                null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                null, null, null);
    }

    private CookingPlanResult.TimelineTask timelineTask(AgentTimelineTask task) {
        return new CookingPlanResult.TimelineTask(
                task.taskId(),
                task.dishId(),
                task.instruction(),
                task.durationMinutes(),
                task.workMode(),
                task.category(),
                task.heatLevel() == null ? "NONE" : task.heatLevel(),
                null,
                task.startMinute(),
                task.endMinute(),
                task.resources());
    }

    private CookingPlanResult.MiseEnPlaceItem miseEnPlace(AgentMiseEnPlaceItem item) {
        return new CookingPlanResult.MiseEnPlaceItem(
                0,
                item.instruction(),
                item.ingredient(),
                item.operation(),
                item.durationMinutes(),
                item.resources(),
                item.whenNeeded());
    }

    private CookingPlanResult.DishCompletion dishCompletion(AgentDishCompletion completion) {
        return new CookingPlanResult.DishCompletion(
                completion.dishId(),
                completion.completionMinute(),
                completion.taskCount(),
                completion.isShared());
    }

    private CookingPlanResult.CompletionItem completionItem(AgentCompletionItem item) {
        return new CookingPlanResult.CompletionItem(
                null,
                item.completionItemId(),
                item.ingredientName(),
                item.recipeIds(),
                item.allocations().stream().map(this::lotAllocation).toList());
    }

    private CookingPlanResult.LotAllocation lotAllocation(AgentLotAllocation allocation) {
        return new CookingPlanResult.LotAllocation(
                null,
                null,
                uuidOrNull(allocation.inventoryLotId()),
                allocation.quantity(),
                allocation.unit(),
                false);
    }

    private CookingPlanResult.Assumption assumption(com.foodmind.foodmindbackend.cooking.domain.agent.AgentAssumption assumption) {
        String evidenceUrl = assumption.evidence().isEmpty() ? null : assumption.evidence().get(0).url();
        return new CookingPlanResult.Assumption(
                0,
                assumption.text(),
                assumption.confidence(),
                evidenceUrl == null ? "LLM_guess" : "evidence",
                evidenceUrl);
    }

    private CookingPlanResult.RepairOption repairOption(AgentRepairOption option) {
        return new CookingPlanResult.RepairOption(
                option.optionId(),
                option.optionType(),
                option.description(),
                option.changes(),
                option.effects(),
                option.revalidationStatus());
    }

    private CookingPlanResult.ConfirmationQuestion confirmationQuestion(AgentConfirmationQuestion question) {
        return new CookingPlanResult.ConfirmationQuestion(
                question.questionId(),
                question.fieldPath(),
                question.prompt(),
                question.responseType(),
                question.options().stream().map(option -> new CookingPlanResult.QuestionOption(
                        option.value(), option.label(), option.suggested())).toList(),
                question.required(),
                question.suggestedValue());
    }

    private List<CookingPlanResult.ConfirmationQuestion> confirmationQuestions(AgentConfirmationPlanResponse response) {
        if (!response.confirmationQuestions().isEmpty()) {
            return response.confirmationQuestions().stream().map(this::confirmationQuestion).toList();
        }
        if (response.decisions().isEmpty()) {
            return List.of();
        }
        List<CookingPlanResult.QuestionOption> options = response.decisions().stream()
                .map(decision -> strategyOption(decision, response.repairOptions()))
                .toList();
        String prompt = response.questions().stream()
                .filter(question -> question != null && !question.isBlank())
                .findFirst()
                .orElse("Choose how to continue.");
        return List.of(new CookingPlanResult.ConfirmationQuestion(
                "repair:strategy",
                "repair_strategy",
                prompt,
                "CHOICE",
                options,
                true,
                options.get(0).value()));
    }

    private CookingPlanResult.QuestionOption strategyOption(
            AgentDecision decision,
            List<AgentRepairOption> repairOptions) {
        for (AgentRepairOption option : repairOptions) {
            if (option.optionId().equals(decision.optionId())) {
                return new CookingPlanResult.QuestionOption(
                        decision.optionId(),
                        option.description(),
                        false);
            }
        }
        return new CookingPlanResult.QuestionOption(
                decision.optionId(),
                fallbackDecisionLabel(decision),
                false);
    }

    private String fallbackDecisionLabel(AgentDecision decision) {
        if ("reduce_servings".equals(decision.optionType())
                && decision.payload() != null
                && decision.payload().get("servings") instanceof Number servings) {
            return "Reduce to " + servings.intValue() + (servings.intValue() == 1 ? " serving" : " servings");
        }
        if ("purchase".equals(decision.optionType())) {
            return "Buy missing ingredients";
        }
        return decision.optionType().replace('_', ' ');
    }

    private List<CookingPlanResult.Decision> decisions(List<AgentDecision> decisions) {
        List<CookingPlanResult.Decision> result = new java.util.ArrayList<>(decisions.size());
        for (int index = 0; index < decisions.size(); index++) {
            AgentDecision decision = decisions.get(index);
            result.add(new CookingPlanResult.Decision(
                    index + 1,
                    decision.optionId(),
                    decision.optionType(),
                    decision.payload(),
                    decision.planRevision()));
        }
        return result;
    }

    private CookingPlanResult.PolicySource policySource(AgentPolicySource source) {
        return new CookingPlanResult.PolicySource(source.sourceId(), source.title(), source.url());
    }

    private static UUID uuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
