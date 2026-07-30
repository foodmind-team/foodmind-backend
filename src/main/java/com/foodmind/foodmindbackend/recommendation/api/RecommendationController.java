package com.foodmind.foodmindbackend.recommendation.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.recommendation.api.request.GenerateRecommendationRequest;
import com.foodmind.foodmindbackend.recommendation.api.response.RecommendationResponse;
import com.foodmind.foodmindbackend.recommendation.api.response.RecommendationSessionSummaryResponse;
import com.foodmind.foodmindbackend.recommendation.application.GenerateRecommendation;
import com.foodmind.foodmindbackend.recommendation.application.GetRecommendation;
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
 * @date: 30/07/2026 06:54 am
 */

@Validated
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final GenerateRecommendation generateRecommendation;
    private final GetRecommendation getRecommendation;

    public RecommendationController(
            GenerateRecommendation generateRecommendation,
            GetRecommendation getRecommendation) {
        this.generateRecommendation = generateRecommendation;
        this.getRecommendation = getRecommendation;
    }

    @PostMapping("/generate")
    ResponseEntity<RecommendationResponse> generate(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GenerateRecommendationRequest request) {
        RecommendationResponse response = RecommendationResponse.from(
                generateRecommendation.handle(principal.id(), request.toContext(), idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/recommendations/" + response.sessionId()))
                .body(response);
    }

    @GetMapping("/{sessionId}")
    RecommendationResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID sessionId) {
        return RecommendationResponse.from(getRecommendation.byId(principal.id(), sessionId));
    }

    @GetMapping("/history")
    PageResponse<RecommendationSessionSummaryResponse> history(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        PageResponse<com.foodmind.foodmindbackend.recommendation.domain.RecommendationSessionSummary> result =
                getRecommendation.history(principal.id(), page, size);
        return PageResponse.of(
                result.items().stream().map(RecommendationSessionSummaryResponse::from).toList(),
                page,
                size,
                result.totalItems());
    }
}
