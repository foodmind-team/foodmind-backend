package com.foodmind.foodmindbackend.recipe.importing.api;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.recipe.importing.api.request.CreateRecipeImportRequest;
import com.foodmind.foodmindbackend.recipe.importing.api.request.RecipeImportAnswersRequest;
import com.foodmind.foodmindbackend.recipe.importing.api.response.RecipeImportResponse;
import com.foodmind.foodmindbackend.recipe.importing.application.AnswerRecipeImport;
import com.foodmind.foodmindbackend.recipe.importing.application.CompleteRecipeImport;
import com.foodmind.foodmindbackend.recipe.importing.application.CreateRecipeImport;
import com.foodmind.foodmindbackend.recipe.importing.application.GetRecipeImport;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recipe-imports")
public class RecipeImportController {
    private final CreateRecipeImport create;
    private final GetRecipeImport get;
    private final AnswerRecipeImport answer;
    private final CompleteRecipeImport complete;

    public RecipeImportController(
            CreateRecipeImport create,
            GetRecipeImport get,
            AnswerRecipeImport answer,
            CompleteRecipeImport complete) {
        this.create = create;
        this.get = get;
        this.answer = answer;
        this.complete = complete;
    }

    @PostMapping
    ResponseEntity<RecipeImportResponse> create(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody CreateRecipeImportRequest request) {
        RecipeImportResponse response = RecipeImportResponse.from(create.handle(principal.id(), request.text()));
        return ResponseEntity.created(URI.create("/api/v1/recipe-imports/" + response.importId()))
                .eTag(etag(response.version()))
                .body(response);
    }

    @GetMapping("/{importId}")
    ResponseEntity<RecipeImportResponse> get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID importId) {
        RecipeImportResponse response = RecipeImportResponse.from(get.handle(principal.id(), importId));
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{importId}/answers")
    ResponseEntity<RecipeImportResponse> answer(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID importId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody RecipeImportAnswersRequest request) {
        RecipeImportResponse response = RecipeImportResponse.from(answer.handle(
                principal.id(), importId, expectedVersion(ifMatch), request.toDomain()));
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{importId}/confirm")
    ResponseEntity<RecipeImportResponse> confirm(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID importId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        RecipeImportResponse response = RecipeImportResponse.from(
                complete.handle(principal.id(), importId, expectedVersion(ifMatch)));
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }

    private long expectedVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "If-Match must contain the expected numeric version.");
        }
    }
}
