package com.foodmind.foodmindbackend.group.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.group.api.request.ShareRecommendationRequest;
import com.foodmind.foodmindbackend.group.api.response.GroupRecommendationShareResponse;
import com.foodmind.foodmindbackend.group.application.GroupRecommendationShareService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@RestController
@RequestMapping("/api/v1/groups/{groupId}/recommendation-shares")
public class GroupRecommendationShareController {

    private final GroupRecommendationShareService shareService;

    public GroupRecommendationShareController(GroupRecommendationShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GroupRecommendationShareResponse share(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody ShareRecommendationRequest request) {
        return GroupRecommendationShareResponse.from(shareService.share(principal.id(), groupId, request.toCommand()));
    }
}
