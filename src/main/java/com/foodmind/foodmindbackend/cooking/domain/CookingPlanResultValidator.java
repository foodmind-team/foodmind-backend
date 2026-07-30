package com.foodmind.foodmindbackend.cooking.domain;

import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentGenerationResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentIngredientResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentStepResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentWarningResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.ValidatedCookingAgentResult;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
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
 * @date: 30/07/2026 12:10 pm
 */

public class CookingPlanResultValidator {

    public static final String SUPPORTED_CONTRACT_VERSION = "cooking-agent-v1";
    private static final int MAX_INGREDIENTS = 50;
    private static final int MAX_STEPS = 30;
    private static final int MAX_WARNINGS = 10;
    private static final EnumSet<WarningCode> ALLOWED_WARNING_CODES = EnumSet.allOf(WarningCode.class);

    public ValidatedCookingAgentResult validate(CookingAgentCommand command, CookingAgentGenerationResult result) {
        if (!result.successful()) {
            validateFailureEnvelope(command, result);
            throw new CookingAgentValidationException(result.failureCode());
        }
        require(command.contractVersion().equals(result.contractVersion()), CookingAgentFailureCode.UNSUPPORTED_VERSION);
        require(SUPPORTED_CONTRACT_VERSION.equals(result.contractVersion()), CookingAgentFailureCode.UNSUPPORTED_VERSION);
        require(command.requestId().equals(result.requestId()), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(command.planId().equals(result.planId()), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(command.traceId().equals(result.traceId()), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require("SUCCEEDED".equals(result.status()), CookingAgentFailureCode.AGENT_UNAVAILABLE);
        require(nonBlank(result.agentTraceId(), 128), CookingAgentFailureCode.SCHEMA_MISMATCH);

        Map<UUID, RecipeCandidate> candidates = command.candidates().stream()
                .collect(Collectors.toMap(candidate -> candidate.recipeId(), candidate -> candidate.recipe(), (left, right) -> left));
        RecipeCandidate selected = candidates.get(result.sourceRecipeId());
        require(selected != null, CookingAgentFailureCode.UNKNOWN_RECIPE);
        validateConstraints(command, result, selected);

        require(!result.ingredients().isEmpty() && result.ingredients().size() <= MAX_INGREDIENTS, CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(!result.steps().isEmpty() && result.steps().size() <= MAX_STEPS, CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(result.warnings().size() <= MAX_WARNINGS, CookingAgentFailureCode.INVALID_WARNING);

        Set<Integer> ingredientSequences = new HashSet<>();
        var ingredients = result.ingredients().stream()
                .sorted(Comparator.comparingInt(CookingAgentIngredientResult::sequenceNo))
                .peek(ingredient -> validateIngredient(ingredient, ingredientSequences))
                .map(ingredient -> new CookingPlanIngredient(
                        ingredient.sequenceNo(),
                        ingredient.ingredientName().trim(),
                        ingredient.quantity(),
                        ingredient.unit() == null ? null : ingredient.unit().trim(),
                        ingredient.availability()))
                .toList();
        requireContiguous(ingredientSequences, result.ingredients().size(), CookingAgentFailureCode.SCHEMA_MISMATCH);

        Set<Integer> stepNumbers = new HashSet<>();
        var steps = result.steps().stream()
                .sorted(Comparator.comparingInt(CookingAgentStepResult::stepNo))
                .peek(step -> validateStep(step, stepNumbers))
                .map(step -> new CookingPlanStep(step.stepNo(), step.instruction().trim()))
                .toList();
        requireContiguous(stepNumbers, result.steps().size(), CookingAgentFailureCode.SCHEMA_MISMATCH);

        Set<Integer> warningSequences = new HashSet<>();
        var warnings = result.warnings().stream()
                .sorted(Comparator.comparingInt(CookingAgentWarningResult::sequenceNo))
                .peek(warning -> validateWarning(warning, warningSequences))
                .map(warning -> new CookingPlanWarning(warning.sequenceNo(), warning.warningCode(), warning.message().trim()))
                .toList();
        requireContiguous(warningSequences, result.warnings().size(), CookingAgentFailureCode.INVALID_WARNING);

        return new ValidatedCookingAgentResult(
                result.contractVersion(),
                result.agentTraceId().trim(),
                result.sourceRecipeId(),
                ingredients,
                steps,
                warnings);
    }

    private void validateFailureEnvelope(CookingAgentCommand command, CookingAgentGenerationResult result) {
        if (result.contractVersion() != null) {
            require(command.contractVersion().equals(result.contractVersion()), CookingAgentFailureCode.UNSUPPORTED_VERSION);
            require(command.requestId().equals(result.requestId()), CookingAgentFailureCode.SCHEMA_MISMATCH);
            require(command.planId().equals(result.planId()), CookingAgentFailureCode.SCHEMA_MISMATCH);
            require(command.traceId().equals(result.traceId()), CookingAgentFailureCode.SCHEMA_MISMATCH);
        }
        if (result.agentTraceId() != null) {
            require(nonBlank(result.agentTraceId(), 128), CookingAgentFailureCode.SCHEMA_MISMATCH);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateConstraints(CookingAgentCommand command, CookingAgentGenerationResult result, RecipeCandidate selected) {
        Map<String, Object> request = command.requestSnapshot();
        require(result.servings() == (int) request.get("servings"), CookingAgentFailureCode.CONSTRAINT_CONFLICT);
        Integer maxMinutes = (Integer) request.get("maxMinutes");
        if (maxMinutes != null) {
            require(result.totalMinutes() != null && result.totalMinutes() <= maxMinutes, CookingAgentFailureCode.CONSTRAINT_CONFLICT);
        }
        BigDecimal maxBudget = (BigDecimal) request.get("maxBudget");
        String currency = (String) request.get("currency");
        if (maxBudget != null) {
            require(result.estimatedCost() != null
                    && result.estimatedCost().compareTo(maxBudget) <= 0
                    && currency.equals(result.currency()), CookingAgentFailureCode.CONSTRAINT_CONFLICT);
        }
        Map<String, Object> constraints = (Map<String, Object>) request.get("constraints");
        for (String requiredCode : (Iterable<String>) constraints.get("requiredDietaryTagCodes")) {
            require(selected.dietaryTagCodes().contains(requiredCode), CookingAgentFailureCode.CONSTRAINT_CONFLICT);
        }
        for (String allergenCode : (Iterable<String>) constraints.get("avoidAllergenCodes")) {
            require(!selected.allergenCodes().contains(allergenCode), CookingAgentFailureCode.CONSTRAINT_CONFLICT);
        }
    }

    private void validateIngredient(CookingAgentIngredientResult ingredient, Set<Integer> seenSequences) {
        require(seenSequences.add(ingredient.sequenceNo()), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(ingredient.sequenceNo() > 0, CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(nonBlank(ingredient.ingredientName(), 160), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require("AVAILABLE".equals(ingredient.availability()) || "TO_BUY".equals(ingredient.availability()), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require((ingredient.quantity() == null) == (ingredient.unit() == null), CookingAgentFailureCode.SCHEMA_MISMATCH);
        if (ingredient.quantity() != null) {
            require(ingredient.quantity().compareTo(BigDecimal.ZERO) > 0 && nonBlank(ingredient.unit(), 40), CookingAgentFailureCode.SCHEMA_MISMATCH);
        }
    }

    private void validateStep(CookingAgentStepResult step, Set<Integer> seenSteps) {
        require(seenSteps.add(step.stepNo()), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(step.stepNo() > 0, CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(nonBlank(step.instruction(), 4000), CookingAgentFailureCode.SCHEMA_MISMATCH);
        require(noUnsupportedSafetyClaim(step.instruction()), CookingAgentFailureCode.SCHEMA_MISMATCH);
    }

    private void validateWarning(CookingAgentWarningResult warning, Set<Integer> seenWarnings) {
        require(seenWarnings.add(warning.sequenceNo()), CookingAgentFailureCode.INVALID_WARNING);
        require(warning.sequenceNo() > 0, CookingAgentFailureCode.INVALID_WARNING);
        require(nonBlank(warning.warningCode(), 80), CookingAgentFailureCode.INVALID_WARNING);
        require(allowedWarningCode(warning.warningCode()), CookingAgentFailureCode.INVALID_WARNING);
        require(nonBlank(warning.message(), 1000), CookingAgentFailureCode.INVALID_WARNING);
        require(noUnsupportedSafetyClaim(warning.message()), CookingAgentFailureCode.INVALID_WARNING);
    }

    private boolean allowedWarningCode(String warningCode) {
        try {
            return ALLOWED_WARNING_CODES.contains(WarningCode.valueOf(warningCode));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void requireContiguous(Set<Integer> values, int size, CookingAgentFailureCode failureCode) {
        for (int sequence = 1; sequence <= size; sequence++) {
            require(values.contains(sequence), failureCode);
        }
    }

    private boolean nonBlank(String value, int maxLength) {
        return value != null && !value.trim().isEmpty() && value.length() <= maxLength;
    }

    private boolean noUnsupportedSafetyClaim(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return !lower.contains("guaranteed")
                && !lower.contains("allergen-free")
                && !lower.contains("allergy-safe")
                && !lower.contains("medical")
                && !lower.contains("food-safe certification");
    }

    private void require(boolean valid, CookingAgentFailureCode failureCode) {
        if (!valid) {
            throw new CookingAgentValidationException(failureCode);
        }
    }

    private enum WarningCode {
        CHECK_ALLERGEN_LABELS,
        MAY_REQUIRE_EXTRA_TIME,
        BUDGET_ESTIMATE_ONLY,
        PANTRY_ITEM_UNVERIFIED,
        COOK_THOROUGHLY
    }

    public static class CookingAgentValidationException extends RuntimeException {

        private final CookingAgentFailureCode failureCode;

        public CookingAgentValidationException(CookingAgentFailureCode failureCode) {
            super(failureCode.name());
            this.failureCode = failureCode;
        }

        public CookingAgentFailureCode failureCode() {
            return failureCode;
        }
    }
}
