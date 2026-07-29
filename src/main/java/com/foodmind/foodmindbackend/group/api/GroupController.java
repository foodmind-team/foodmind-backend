package com.foodmind.foodmindbackend.group.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.group.api.request.CreateGroupRequest;
import com.foodmind.foodmindbackend.group.api.request.UpdateGroupRequest;
import com.foodmind.foodmindbackend.group.api.response.GroupResponse;
import com.foodmind.foodmindbackend.group.application.GroupService;
import com.foodmind.foodmindbackend.group.domain.TrustedGroup;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    ResponseEntity<GroupResponse> create(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody CreateGroupRequest request) {
        TrustedGroup group = groupService.create(principal.id(), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/groups/" + group.id()))
                .body(GroupResponse.from(group));
    }

    @GetMapping
    List<GroupResponse> list(@AuthenticationPrincipal FoodMindPrincipal principal) {
        return groupService.list(principal.id()).stream().map(GroupResponse::from).toList();
    }

    @GetMapping("/{groupId}")
    GroupResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID groupId) {
        return GroupResponse.from(groupService.get(principal.id(), groupId));
    }

    @PatchMapping("/{groupId}")
    GroupResponse update(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        return GroupResponse.from(groupService.update(principal.id(), groupId, request.toCommand()));
    }
}
