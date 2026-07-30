package com.foodmind.foodmindbackend.cooking.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.cooking.api.request.GenerateCookingPlanRequest;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanResponse;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanSummaryResponse;
import com.foodmind.foodmindbackend.cooking.application.GenerateCookingPlan;
import com.foodmind.foodmindbackend.cooking.application.GetCookingPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Validated
@RestController
@RequestMapping("/api/v1/cooking-plans")
public class CookingPlanController {

    private final GenerateCookingPlan generateCookingPlan;
    private final GetCookingPlan getCookingPlan;

    public CookingPlanController(GenerateCookingPlan generateCookingPlan, GetCookingPlan getCookingPlan) {
        this.generateCookingPlan = generateCookingPlan;
        this.getCookingPlan = getCookingPlan;
    }

    @PostMapping("/generate")
    ResponseEntity<CookingPlanResponse> generate(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GenerateCookingPlanRequest request) {
        CookingPlanResponse response = CookingPlanResponse.from(
                generateCookingPlan.handle(principal.id(), request.toContext(), idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/cooking-plans/" + response.planId()))
                .body(response);
    }

    @GetMapping("/{planId}")
    CookingPlanResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId) {
        return CookingPlanResponse.from(getCookingPlan.handle(principal.id(), planId));
    }

    @GetMapping("/history")
    PageResponse<CookingPlanSummaryResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        PageResponse<com.foodmind.foodmindbackend.cooking.domain.CookingPlanSummary> result =
                getCookingPlan.history(principal.id(), page, size);
        return PageResponse.of(
                result.items().stream().map(CookingPlanSummaryResponse::from).toList(),
                page,
                size,
                result.totalItems());
    }
}
