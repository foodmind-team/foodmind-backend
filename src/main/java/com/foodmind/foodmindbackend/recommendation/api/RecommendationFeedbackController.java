package com.foodmind.foodmindbackend.recommendation.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.recommendation.api.request.RecommendationFeedbackRequest;
import com.foodmind.foodmindbackend.recommendation.api.response.RecommendationFeedbackResponse;
import com.foodmind.foodmindbackend.recommendation.application.SubmitFeedback;
import com.foodmind.foodmindbackend.recommendation.application.SubmitFeedback.SubmitFeedbackResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationFeedbackController {

    private final SubmitFeedback submitFeedback;

    public RecommendationFeedbackController(SubmitFeedback submitFeedback) {
        this.submitFeedback = submitFeedback;
    }

    @PostMapping("/{sessionId}/feedback")
    ResponseEntity<RecommendationFeedbackResponse> submit(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecommendationFeedbackRequest request) {
        SubmitFeedbackResult result = submitFeedback.handle(principal.id(), request.toCommand(sessionId), idempotencyKey);
        RecommendationFeedbackResponse response = RecommendationFeedbackResponse.from(result);
        if (!result.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/recommendations/" + sessionId + "/feedback/" + response.feedbackId()))
                .body(response);
    }
}
