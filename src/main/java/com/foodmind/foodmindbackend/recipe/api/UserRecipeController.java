package com.foodmind.foodmindbackend.recipe.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.recipe.api.request.UserRecipeRequest;
import com.foodmind.foodmindbackend.recipe.api.response.UserRecipeResponse;
import com.foodmind.foodmindbackend.recipe.application.CreateUserRecipe;
import com.foodmind.foodmindbackend.recipe.application.DeleteUserRecipe;
import com.foodmind.foodmindbackend.recipe.application.GetUserRecipe;
import com.foodmind.foodmindbackend.recipe.application.ListUserRecipes;
import com.foodmind.foodmindbackend.recipe.application.UpdateUserRecipe;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipePage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recipes")
public class UserRecipeController {
    private final CreateUserRecipe create;
    private final GetUserRecipe get;
    private final ListUserRecipes list;
    private final UpdateUserRecipe update;
    private final DeleteUserRecipe delete;

    public UserRecipeController(CreateUserRecipe create, GetUserRecipe get, ListUserRecipes list, UpdateUserRecipe update, DeleteUserRecipe delete) {
        this.create = create; this.get = get; this.list = list; this.update = update; this.delete = delete;
    }

    @PostMapping
    ResponseEntity<UserRecipeResponse> create(@AuthenticationPrincipal FoodMindPrincipal principal, @Valid @RequestBody UserRecipeRequest request) {
        UserRecipe recipe = create.handle(principal.id(), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/recipes/" + recipe.id())).eTag(etag(recipe.version())).body(UserRecipeResponse.from(recipe));
    }

    @GetMapping
    PageResponse<UserRecipeResponse> list(@AuthenticationPrincipal FoodMindPrincipal principal,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        UserRecipePage result = list.handle(principal.id(), page, size);
        return PageResponse.of(result.items().stream().map(UserRecipeResponse::from).toList(), page, size, result.totalItems());
    }

    @GetMapping("/{id}")
    ResponseEntity<UserRecipeResponse> get(@AuthenticationPrincipal FoodMindPrincipal principal, @PathVariable UUID id) {
        UserRecipe recipe = get.handle(principal.id(), id);
        return ResponseEntity.ok().eTag(etag(recipe.version())).body(UserRecipeResponse.from(recipe));
    }

    @PutMapping("/{id}")
    ResponseEntity<UserRecipeResponse> update(@AuthenticationPrincipal FoodMindPrincipal principal, @PathVariable UUID id,
                                               @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
                                               @Valid @RequestBody UserRecipeRequest request) {
        UserRecipe recipe = update.handle(principal.id(), id, expectedVersion(ifMatch), request.toCommand());
        return ResponseEntity.ok().eTag(etag(recipe.version())).body(UserRecipeResponse.from(recipe));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal FoodMindPrincipal principal, @PathVariable UUID id) {
        delete.handle(principal.id(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }
    private long expectedVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) normalized = normalized.substring(1, normalized.length() - 1);
        try { return Long.parseLong(normalized); }
        catch (NumberFormatException exception) { throw new ApiException(ErrorCode.VALIDATION_ERROR, "If-Match must contain the expected numeric version."); }
    }
}
