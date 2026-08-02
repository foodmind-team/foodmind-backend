package com.foodmind.foodmindbackend.cooking.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent-native cooking plan aggregate root. The four terminal statuses are
 * READY / NEEDS_CONFIRMATION / INFEASIBLE / FAILED; sub-data is materialised
 * from the agent response and read back through the repository.
 */
public record CookingPlanResult(
        UUID planId,
        String status,
        String planRevision,
        String region,
        LocalDate cookingDate,
        OffsetDateTime servingAt,
        Integer timeLimitMinutes,
        String solverStatus,
        Integer makespanMinutes,
        String correlationId,
        String schemaVersion,
        String errorCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        List<Source> sources,
        List<TimelineTask> timeline,
        List<MiseEnPlaceItem> miseEnPlace,
        List<DishCompletion> dishCompletions,
        List<CompletionItem> completionChecklist,
        List<Assumption> assumptions,
        List<RepairOption> repairOptions,
        List<String> questions,
        List<ConfirmationQuestion> confirmationQuestions,
        List<Decision> decisions,
        List<String> reasons,
        List<String> safeAlternatives,
        SafetyPolicy safetyPolicy,
        String explanation,
        String explanationSource) {

    public CookingPlanResult {
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

    /** Immutable snapshot of one recipe text sent to the agent. */
    public record Source(
            int sequenceNo,
            String sourceType,
            UUID sourceId,
            BigDecimal targetServings,
            String dishName,
            String recipeText) {
    }

    /** One scheduled task on the READY timeline. */
    public record TimelineTask(
            String taskId,
            String dishId,
            String instruction,
            int durationMinutes,
            String workMode,
            String category,
            String heatLevel,
            BigDecimal targetTemperatureC,
            Integer startMinute,
            Integer endMinute,
            List<String> resources) {

        public TimelineTask {
            resources = resources == null ? List.of() : List.copyOf(resources);
        }
    }

    /** One mise-en-place (prep-ahead) entry of a READY plan. */
    public record MiseEnPlaceItem(
            int sequenceNo,
            String instruction,
            String ingredient,
            String operation,
            Integer durationMinutes,
            List<String> resources,
            String whenNeeded) {

        public MiseEnPlaceItem {
            resources = resources == null ? List.of() : List.copyOf(resources);
        }
    }

    /** One dish completion summary entry of a READY plan. */
    public record DishCompletion(
            String dishId,
            int completionMinute,
            int taskCount,
            boolean isShared) {
    }

    /** One completion-checklist entry grouping lot allocations for an ingredient. */
    public record CompletionItem(
            UUID id,
            String completionItemId,
            String ingredientName,
            List<String> recipeIds,
            List<LotAllocation> allocations) {

        public CompletionItem {
            recipeIds = recipeIds == null ? List.of() : List.copyOf(recipeIds);
            allocations = allocations == null ? List.of() : List.copyOf(allocations);
        }
    }

    /** A proposed deduction from a specific inventory lot (plan, not a mutation). */
    public record LotAllocation(
            UUID id,
            UUID completionItemId,
            UUID inventoryLotId,
            BigDecimal quantity,
            String unit,
            boolean isReserved) {
    }

    /** One assumption surfaced by a NEEDS_CONFIRMATION plan. */
    public record Assumption(
            int sequenceNo,
            String text,
            BigDecimal confidence,
            String sourceType,
            String evidenceUrl) {
    }

    /** One validated repair option of a NEEDS_CONFIRMATION plan. */
    public record RepairOption(
            String optionId,
            String optionType,
            String description,
            List<String> changes,
            List<String> effects,
            String revalidationStatus) {

        public RepairOption {
            changes = changes == null ? List.of() : List.copyOf(changes);
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }

    /** One field-level confirmation question of a NEEDS_CONFIRMATION plan. */
    public record ConfirmationQuestion(
            String questionId,
            String fieldPath,
            String prompt,
            String responseType,
            List<QuestionOption> options,
            boolean required,
            String suggestedValue) {

        public ConfirmationQuestion {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** One selectable answer for a CHOICE confirmation question. */
    public record QuestionOption(
            String value,
            String label,
            boolean suggested) {
    }

    /** One structured, client-submittable decision of a NEEDS_CONFIRMATION plan. */
    public record Decision(
            int sequenceNo,
            String optionId,
            String optionType,
            Map<String, Object> payload,
            String planRevision) {

        public Decision {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    /** Safety-policy provenance attached to READY / NEEDS_CONFIRMATION plans. */
    public record SafetyPolicy(
            String region,
            String version,
            LocalDate effectiveAt,
            List<PolicySource> sources) {

        public SafetyPolicy {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    /** Serialisable reference to an official safety-policy source. */
    public record PolicySource(
            String sourceId,
            String title,
            String url) {
    }
}
