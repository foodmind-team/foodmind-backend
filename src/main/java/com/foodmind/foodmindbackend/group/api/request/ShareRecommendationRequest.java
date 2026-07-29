package com.foodmind.foodmindbackend.group.api.request;

import com.foodmind.foodmindbackend.group.application.GroupRecommendationShareService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record ShareRecommendationRequest(
        @NotNull UUID recommendationCandidateId,
        @Size(max = 2000) String message) {

    public GroupRecommendationShareService.Command toCommand() {
        return new GroupRecommendationShareService.Command(recommendationCandidateId, message);
    }
}
