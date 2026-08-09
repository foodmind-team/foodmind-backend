package com.foodmind.foodmindbackend.recipe.importing.api.request;

import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RecipeImportAnswersRequest(
        @NotEmpty @Size(max = 24) List<@Valid Answer> answers) {
    public List<RecipeImportAnswer> toDomain() {
        return answers.stream()
                .map(answer -> new RecipeImportAnswer(answer.questionId(), answer.value()))
                .toList();
    }

    public record Answer(
            @NotBlank @Size(max = 160) String questionId,
            @NotBlank @Size(max = 20_000) String value) {
    }
}
