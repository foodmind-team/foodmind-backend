package com.foodmind.foodmindbackend.user.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.user.api.response.CurrentUserResponse;
import com.foodmind.foodmindbackend.user.application.GetCurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public CurrentUserController(GetCurrentUser getCurrentUser) {
        this.getCurrentUser = getCurrentUser;
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal FoodMindPrincipal principal) {
        return CurrentUserResponse.from(getCurrentUser.get(principal.id()));
    }
}
