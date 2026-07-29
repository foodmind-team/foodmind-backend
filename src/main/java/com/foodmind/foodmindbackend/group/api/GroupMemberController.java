package com.foodmind.foodmindbackend.group.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.group.api.response.GroupMemberResponse;
import com.foodmind.foodmindbackend.group.application.GroupMemberService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/v1/groups/{groupId}/members")
public class GroupMemberController {

    private final GroupMemberService memberService;

    public GroupMemberController(GroupMemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    List<GroupMemberResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID groupId) {
        return memberService.list(principal.id(), groupId).stream().map(GroupMemberResponse::from).toList();
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        memberService.remove(principal.id(), groupId, userId);
    }
}
