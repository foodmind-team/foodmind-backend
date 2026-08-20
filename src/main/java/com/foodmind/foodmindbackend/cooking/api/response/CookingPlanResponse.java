package com.foodmind.foodmindbackend.cooking.api.response;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public cooking-plan response covering all four agent-native terminal statuses.
 * Fields not applicable to a status are null / empty (clients branch on {@code status}).
 */
public record CookingPlanResponse(
        UUID planId,
        String status,
        String planRevision,
        String region,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        OffsetDateTime finishedAt,
        UUID reusedFromPlanId,
        String solverStatus,
        Integer makespanMinutes,
        String errorCode,
        String errorMessage,
        List<SourceResponse> sources,
        List<TimelineTaskResponse> timeline,
        List<MiseEnPlaceItemResponse> miseEnPlace,
        List<DishCompletionResponse> dishCompletions,
        List<CompletionItemResponse> completionChecklist,
        List<AssumptionResponse> assumptions,
        List<RepairOptionResponse> repairOptions,
        List<String> questions,
        List<ConfirmationQuestionResponse> confirmationQuestions,
        List<DecisionResponse> decisions,
        List<String> reasons,
        List<String> safeAlternatives,
        SafetyPolicyResponse safetyPolicy,
        String explanation,
        String explanationSource) {

    public CookingPlanResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        miseEnPlace = miseEnPlace == null ? List.of() : List.copyOf(miseEnPlace);
        dishCompletions = dishCompletions == null ? List.of() : List.copyOf(dishCompletions);
        completionChecklist = completionChecklist == null ? List.of() : List.copyOf(completionChecklist);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        repairOptions = repairOptions == null ? List.of() : List.copyOf(repairOptions);
        questions = questions == null ? List.of() : List.copyOf(questions);
        confirmationQuestions = confirmationQuestions == null ? List.of() : List.copyOf(confirmationQuestions);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        safeAlternatives = safeAlternatives == null ? List.of() : List.copyOf(safeAlternatives);
    }

    public static CookingPlanResponse from(CookingPlanResult result) {
        return new CookingPlanResponse(
                result.planId(),
                result.status(),
                result.planRevision(),
                result.region(),
                result.createdAt(),
                result.completedAt(),
                result.finishedAt(),
                result.reusedFromPlanId(),
                result.solverStatus(),
                result.makespanMinutes(),
                result.errorCode(),
                result.errorMessage(),
                result.sources().stream().map(SourceResponse::from).toList(),
                result.timeline().stream().map(TimelineTaskResponse::from).toList(),
                result.miseEnPlace().stream().map(MiseEnPlaceItemResponse::from).toList(),
                result.dishCompletions().stream().map(DishCompletionResponse::from).toList(),
                result.completionChecklist().stream().map(CompletionItemResponse::from).toList(),
                result.assumptions().stream().map(AssumptionResponse::from).toList(),
                result.repairOptions().stream().map(RepairOptionResponse::from).toList(),
                result.questions(),
                result.confirmationQuestions().stream().map(ConfirmationQuestionResponse::from).toList(),
                result.decisions().stream().map(DecisionResponse::from).toList(),
                result.reasons(),
                result.safeAlternatives(),
                result.safetyPolicy() == null ? null : SafetyPolicyResponse.from(result.safetyPolicy()),
                result.explanation(),
                result.explanationSource());
    }

    public record SourceResponse(
            int sequenceNo,
            String sourceType,
            UUID sourceId,
            BigDecimal targetServings,
            String dishName) {

        static SourceResponse from(CookingPlanResult.Source source) {
            return new SourceResponse(
                    source.sequenceNo(), source.sourceType(), source.sourceId(),
                    source.targetServings(), source.dishName());
        }
    }

    public record TimelineTaskResponse(
            String taskId,
            int startMinute,
            int endMinute,
            int durationMinutes,
            String instruction,
            String dishId,
            String workMode,
            String category,
            String heatLevel,
            List<String> resources) {

        static TimelineTaskResponse from(CookingPlanResult.TimelineTask task) {
            return new TimelineTaskResponse(
                    task.taskId(), task.startMinute(), task.endMinute(), task.durationMinutes(),
                    task.instruction(), task.dishId(), task.workMode(), task.category(),
                    task.heatLevel(), task.resources());
        }
    }

    public record MiseEnPlaceItemResponse(
            int sequenceNo,
            String instruction,
            String ingredient,
            String operation,
            Integer durationMinutes,
            List<String> resources,
            String whenNeeded) {

        static MiseEnPlaceItemResponse from(CookingPlanResult.MiseEnPlaceItem item) {
            return new MiseEnPlaceItemResponse(
                    item.sequenceNo(), item.instruction(), item.ingredient(), item.operation(),
                    item.durationMinutes(), item.resources(), item.whenNeeded());
        }
    }

    public record DishCompletionResponse(
            String dishId,
            int completionMinute,
            int taskCount,
            boolean isShared) {

        static DishCompletionResponse from(CookingPlanResult.DishCompletion completion) {
            return new DishCompletionResponse(
                    completion.dishId(), completion.completionMinute(),
                    completion.taskCount(), completion.isShared());
        }
    }

    public record CompletionItemResponse(
            String completionItemId,
            String ingredientName,
            List<String> recipeIds,
            List<LotAllocationResponse> allocations) {

        static CompletionItemResponse from(CookingPlanResult.CompletionItem item) {
            return new CompletionItemResponse(
                    item.completionItemId(), item.ingredientName(), item.recipeIds(),
                    item.allocations().stream().map(LotAllocationResponse::from).toList());
        }
    }

    public record LotAllocationResponse(
            String inventoryLotId,
            BigDecimal quantity,
            String unit) {

        static LotAllocationResponse from(CookingPlanResult.LotAllocation allocation) {
            return new LotAllocationResponse(
                    allocation.inventoryLotId().toString(), allocation.quantity(), allocation.unit());
        }
    }

    public record AssumptionResponse(
            String text,
            BigDecimal confidence,
            String sourceType,
            String evidenceUrl) {

        static AssumptionResponse from(CookingPlanResult.Assumption assumption) {
            return new AssumptionResponse(
                    assumption.text(), assumption.confidence(),
                    assumption.sourceType(), assumption.evidenceUrl());
        }
    }

    public record RepairOptionResponse(
            String optionId,
            String optionType,
            String description,
            List<String> changes,
            List<String> effects,
            String revalidationStatus) {

        static RepairOptionResponse from(CookingPlanResult.RepairOption option) {
            return new RepairOptionResponse(
                    option.optionId(), option.optionType(), option.description(),
                    option.changes(), option.effects(), option.revalidationStatus());
        }
    }

    public record ConfirmationQuestionResponse(
            String questionId,
            String fieldPath,
            String prompt,
            String responseType,
            List<QuestionOptionResponse> options,
            boolean required,
            String suggestedValue) {

        static ConfirmationQuestionResponse from(CookingPlanResult.ConfirmationQuestion question) {
            return new ConfirmationQuestionResponse(
                    question.questionId(), question.fieldPath(), question.prompt(), question.responseType(),
                    question.options().stream().map(QuestionOptionResponse::from).toList(),
                    question.required(), question.suggestedValue());
        }
    }

    public record QuestionOptionResponse(
            String value,
            String label,
            boolean suggested) {

        static QuestionOptionResponse from(CookingPlanResult.QuestionOption option) {
            return new QuestionOptionResponse(option.value(), option.label(), option.suggested());
        }
    }

    public record DecisionResponse(
            String optionId,
            String optionType,
            Map<String, Object> payload,
            String planRevision) {

        static DecisionResponse from(CookingPlanResult.Decision decision) {
            return new DecisionResponse(
                    decision.optionId(), decision.optionType(),
                    decision.payload(), decision.planRevision());
        }
    }

    public record SafetyPolicyResponse(
            String region,
            String version,
            LocalDate effectiveAt,
            List<PolicySourceResponse> sources) {

        static SafetyPolicyResponse from(CookingPlanResult.SafetyPolicy policy) {
            return new SafetyPolicyResponse(
                    policy.region(), policy.version(), policy.effectiveAt(),
                    policy.sources().stream().map(PolicySourceResponse::from).toList());
        }
    }

    public record PolicySourceResponse(
            String sourceId,
            String title,
            String url) {

        static PolicySourceResponse from(CookingPlanResult.PolicySource source) {
            return new PolicySourceResponse(source.sourceId(), source.title(), source.url());
        }
    }
}
