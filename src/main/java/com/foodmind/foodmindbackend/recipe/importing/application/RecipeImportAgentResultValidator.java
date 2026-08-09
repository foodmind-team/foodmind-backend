package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportAgentPort;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.util.HashSet;

final class RecipeImportAgentResultValidator {
    private RecipeImportAgentResultValidator() {
    }

    static void validate(RecipeImportAgentPort.Result result) {
        if (result == null || result.drafts().isEmpty() || result.drafts().size() > 6) {
            throw invalidAgentResult();
        }
        HashSet<String> draftIds = new HashSet<>();
        if (result.drafts().stream().anyMatch(draft -> draft.draftId() == null || !draftIds.add(draft.draftId()))) {
            throw invalidAgentResult();
        }
        if (result.status() == RecipeImportStatus.READY) {
            if (!result.questions().isEmpty() || result.drafts().stream().anyMatch(draft -> !draft.ready())) {
                throw invalidAgentResult();
            }
            return;
        }
        if (result.status() != RecipeImportStatus.NEEDS_CLARIFICATION || result.questions().isEmpty()) {
            throw invalidAgentResult();
        }
        HashSet<String> questionIds = new HashSet<>();
        if (result.questions().stream().anyMatch(question -> question.questionId() == null
                || !questionIds.add(question.questionId())
                || !draftIds.contains(question.draftId()))) {
            throw invalidAgentResult();
        }
    }

    private static ApiException invalidAgentResult() {
        return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Recipe parsing returned an invalid result. Please try again.");
    }
}
