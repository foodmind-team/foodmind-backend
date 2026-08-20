package com.foodmind.foodmindbackend.cooking.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.cooking.api.request.GenerateCookingPlanRequest;
import com.foodmind.foodmindbackend.cooking.api.request.SubmitDecisionsRequest.QuestionAnswer;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanAsyncAcceptedResponse;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanInventoryConsumptionResponse;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanResponse;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanSummaryResponse;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanTaskProgressResponse;
import com.foodmind.foodmindbackend.cooking.api.response.CookingPlanTaskResponse;
import com.foodmind.foodmindbackend.cooking.application.CancelCookingPlanTask;
import com.foodmind.foodmindbackend.cooking.application.ConsumeCookingPlanInventory;
import com.foodmind.foodmindbackend.cooking.application.GenerateCookingPlan;
import com.foodmind.foodmindbackend.cooking.application.GetCookingPlan;
import com.foodmind.foodmindbackend.cooking.application.GetCookingPlanTask;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.util.List;
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
    private final GetCookingPlanTask getCookingPlanTask;
    private final CancelCookingPlanTask cancelCookingPlanTask;
    private final ConsumeCookingPlanInventory consumeCookingPlanInventory;

    public CookingPlanController(
            GenerateCookingPlan generateCookingPlan,
            GetCookingPlan getCookingPlan,
            GetCookingPlanTask getCookingPlanTask,
            CancelCookingPlanTask cancelCookingPlanTask,
            ConsumeCookingPlanInventory consumeCookingPlanInventory) {
        this.generateCookingPlan = generateCookingPlan;
        this.getCookingPlan = getCookingPlan;
        this.getCookingPlanTask = getCookingPlanTask;
        this.cancelCookingPlanTask = cancelCookingPlanTask;
        this.consumeCookingPlanInventory = consumeCookingPlanInventory;
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

    @PostMapping("/generate-async")
    ResponseEntity<Object> generateAsync(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GenerateCookingPlanRequest request) {
        GenerateCookingPlan.AsyncSubmitResult result =
                generateCookingPlan.submitAsync(principal.id(), request.toContext(), idempotencyKey);
        return asyncResponse(result);
    }

    private ResponseEntity<Object> asyncResponse(GenerateCookingPlan.AsyncSubmitResult result) {
        if (result instanceof GenerateCookingPlan.AsyncSubmitResult.Accepted accepted) {
            CookingPlanAsyncAcceptedResponse body = new CookingPlanAsyncAcceptedResponse(
                    accepted.planId(), accepted.status(), accepted.taskId(),
                    "/api/v1/cooking-plans/" + accepted.planId() + "/task");
            return ResponseEntity.accepted().body(body);
        }
        return ResponseEntity.ok(CookingPlanResponse.from(
                ((GenerateCookingPlan.AsyncSubmitResult.RejectedPlan) result).plan()));
    }

    @GetMapping("/{planId}/task")
    CookingPlanTaskResponse getTask(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId) {
        CookingPlanRepository.GenerationRow generation = getCookingPlanTask.handle(principal.id(), planId);
        return new CookingPlanTaskResponse(
                generation.planId(),
                generation.taskId(),
                "PROCESSING",
                generation.syncState(),
                new CookingPlanTaskProgressResponse(
                        generation.lastProgressNode(),
                        generation.lastProgressSteps(),
                        generation.lastProgressMessage()));
    }

    @PostMapping("/{planId}/cancel")
    CookingPlanResponse cancel(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId) {
        return CookingPlanResponse.from(cancelCookingPlanTask.handle(principal.id(), planId));
    }

    @PostMapping("/{planId}/consume-inventory")
    CookingPlanInventoryConsumptionResponse consumeInventory(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId) {
        return CookingPlanInventoryConsumptionResponse.from(consumeCookingPlanInventory.handle(principal.id(), planId));
    }

    @PostMapping("/{planId}/finish")
    CookingPlanResponse finish(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId) {
        consumeCookingPlanInventory.handle(principal.id(), planId);
        return CookingPlanResponse.from(getCookingPlan.handle(principal.id(), planId));
    }

    @GetMapping("/{planId}")
    CookingPlanResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId) {
        return CookingPlanResponse.from(getCookingPlan.handle(principal.id(), planId));
    }

    @PostMapping("/{planId}/decisions")
    CookingPlanResponse submitDecisions(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @NotEmpty @RequestBody List<@Valid QuestionAnswer> answers) {
        return CookingPlanResponse.from(generateCookingPlan.submitDecisions(
                principal.id(), planId, answers, idempotencyKey));
    }

    @PostMapping("/{planId}/decisions-async")
    ResponseEntity<Object> submitDecisionsAsync(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID planId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @NotEmpty @RequestBody List<@Valid QuestionAnswer> answers) {
        return asyncResponse(generateCookingPlan.submitDecisionsAsync(
                principal.id(), planId, answers, idempotencyKey));
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
