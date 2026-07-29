package com.foodmind.foodmindbackend.group.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.group.api.request.CreateInvitationRequest;
import com.foodmind.foodmindbackend.group.api.request.JoinGroupRequest;
import com.foodmind.foodmindbackend.group.api.response.GroupInvitationResponse;
import com.foodmind.foodmindbackend.group.api.response.GroupMemberResponse;
import com.foodmind.foodmindbackend.group.application.GroupInvitationService;
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
@RequestMapping("/api/v1")
public class GroupInvitationController {

    private final GroupInvitationService invitationService;

    public GroupInvitationController(GroupInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/groups/{groupId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    GroupInvitationResponse create(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateInvitationRequest request) {
        return GroupInvitationResponse.from(invitationService.create(principal.id(), groupId, request.toCommand()));
    }

    @PostMapping({"/group-invitations/join", "/groups/join"})
    GroupMemberResponse join(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody JoinGroupRequest request) {
        return GroupMemberResponse.from(invitationService.join(principal.id(), request.token()));
    }
}
