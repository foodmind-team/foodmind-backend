package com.foodmind.foodmindbackend.user.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.user.api.request.UpdateCurrentUserRequest;
import com.foodmind.foodmindbackend.user.api.response.CurrentUserResponse;
import com.foodmind.foodmindbackend.user.application.GetCurrentUser;
import com.foodmind.foodmindbackend.user.application.UpdateCurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {

    private final GetCurrentUser getCurrentUser;
    private final UpdateCurrentUser updateCurrentUser;

    public CurrentUserController(GetCurrentUser getCurrentUser, UpdateCurrentUser updateCurrentUser) {
        this.getCurrentUser = getCurrentUser;
        this.updateCurrentUser = updateCurrentUser;
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal FoodMindPrincipal principal) {
        return CurrentUserResponse.from(getCurrentUser.get(principal.id()));
    }

    @PatchMapping("/me")
    CurrentUserResponse updateMe(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody UpdateCurrentUserRequest request) {
        return CurrentUserResponse.from(updateCurrentUser.update(principal.id(), request.displayName(), request.timeZone()));
    }
}
