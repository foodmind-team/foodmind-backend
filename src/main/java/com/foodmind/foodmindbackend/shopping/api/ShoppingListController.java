package com.foodmind.foodmindbackend.shopping.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanAsyncAcceptedResponse;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanResponse;
import com.foodmind.foodmindbackend.cooking.application.GenerateCookingPlan;
import com.foodmind.foodmindbackend.shopping.api.request.UpdateShoppingListItemRequest;
import com.foodmind.foodmindbackend.shopping.api.response.ShoppingListResponse;
import com.foodmind.foodmindbackend.shopping.application.CompleteShoppingList;
import com.foodmind.foodmindbackend.shopping.application.GetOrCreateShoppingList;
import com.foodmind.foodmindbackend.shopping.application.GetShoppingList;
import com.foodmind.foodmindbackend.shopping.application.ListShoppingLists;
import com.foodmind.foodmindbackend.shopping.application.UpdateShoppingListItem;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingListPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class ShoppingListController {
    private final GetOrCreateShoppingList getOrCreate;
    private final GetShoppingList get;
    private final ListShoppingLists list;
    private final UpdateShoppingListItem updateItem;
    private final CompleteShoppingList complete;

    public ShoppingListController(
            GetOrCreateShoppingList getOrCreate,
            GetShoppingList get,
            ListShoppingLists list,
            UpdateShoppingListItem updateItem,
            CompleteShoppingList complete) {
        this.getOrCreate = getOrCreate;
        this.get = get;
        this.list = list;
        this.updateItem = updateItem;
        this.complete = complete;
    }

    @PostMapping("/api/v1/cooking-plans/{planId}/shopping-list")
    ResponseEntity<ShoppingListResponse> getOrCreate(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId) {
        ShoppingList result = getOrCreate.handle(principal.id(), planId);
        return ResponseEntity.created(URI.create("/api/v1/shopping-lists/" + result.id()))
                .eTag(etag(result.version()))
                .body(ShoppingListResponse.from(result));
    }

    @GetMapping("/api/v1/shopping-lists")
    PageResponse<ShoppingListResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        ShoppingListPage result = list.handle(principal.id(), status, page, size);
        return PageResponse.of(result.items().stream().map(ShoppingListResponse::from).toList(),
                page, size, result.totalItems());
    }

    @GetMapping("/api/v1/shopping-lists/{shoppingListId}")
    ResponseEntity<ShoppingListResponse> get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID shoppingListId) {
        ShoppingList result = get.handle(principal.id(), shoppingListId);
        return ResponseEntity.ok().eTag(etag(result.version())).body(ShoppingListResponse.from(result));
    }

    @PatchMapping("/api/v1/shopping-lists/{shoppingListId}/items/{itemId}")
    ResponseEntity<ShoppingListResponse> updateItem(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID shoppingListId,
            @PathVariable UUID itemId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateShoppingListItemRequest request) {
        ShoppingList result = updateItem.handle(
                principal.id(), shoppingListId, itemId, expectedVersion(ifMatch), request.toCommand());
        return ResponseEntity.ok().eTag(etag(result.version())).body(ShoppingListResponse.from(result));
    }

    @PostMapping("/api/v1/shopping-lists/{shoppingListId}/complete")
    ResponseEntity<Object> complete(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID shoppingListId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        GenerateCookingPlan.AsyncSubmitResult result = complete.handle(
                principal.id(), shoppingListId, idempotencyKey);
        if (result instanceof GenerateCookingPlan.AsyncSubmitResult.Accepted accepted) {
            return ResponseEntity.accepted().body(new CookingPlanAsyncAcceptedResponse(
                    accepted.planId(), accepted.status(), accepted.taskId(),
                    "/api/v1/cooking-plans/" + accepted.planId() + "/task"));
        }
        return ResponseEntity.ok(CookingPlanResponse.from(
                ((GenerateCookingPlan.AsyncSubmitResult.RejectedPlan) result).plan()));
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
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "If-Match must contain the expected numeric version.");
        }
    }
}
