package com.foodmind.foodmindbackend.group.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.group.api.response.GroupFeedResponse;
import com.foodmind.foodmindbackend.group.application.GroupFeedService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@Validated
@RestController
@RequestMapping("/api/v1/groups/{groupId}/feed")
public class GroupFeedController {

    private final GroupFeedService groupFeedService;

    public GroupFeedController(GroupFeedService groupFeedService) {
        this.groupFeedService = groupFeedService;
    }

    @GetMapping
    GroupFeedResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID groupId,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return GroupFeedResponse.from(groupFeedService.get(principal.id(), groupId, after, limit));
    }
}
