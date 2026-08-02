package com.foodmind.foodmindbackend.cooking.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Confirmation decision submission for a NEEDS_CONFIRMATION plan.
 * The wire body is a bare array of {@link QuestionAnswer} items
 * ({@code [{ "questionId": "q-1", "value": "accept" }]}).
 */
public record SubmitDecisionsRequest(
        @NotEmpty
        List<@Valid QuestionAnswer> answers) {

    public SubmitDecisionsRequest {
        answers = answers == null ? List.of() : List.copyOf(answers);
    }

    /** One answer to a presented confirmation question. */
    public record QuestionAnswer(
            @NotBlank
            @Size(max = 128)
            String questionId,
            @NotBlank
            @Size(max = 500)
            String value) {
    }
}
