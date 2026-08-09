package com.foodmind.foodmindbackend.recipe.importing.application.port;

import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportQuestion;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.util.List;

public interface RecipeImportAgentPort {
    Result parse(String requestId, String text, List<RecipeImportAnswer> answers);

    record Result(
            RecipeImportStatus status,
            List<RecipeImportDraft> drafts,
            List<RecipeImportQuestion> questions) {
        public Result {
            drafts = List.copyOf(drafts);
            questions = List.copyOf(questions);
        }
    }
}
