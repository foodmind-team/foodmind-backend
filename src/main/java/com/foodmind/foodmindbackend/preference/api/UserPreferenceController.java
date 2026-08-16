package com.foodmind.foodmindbackend.preference.api;

import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.preference.api.request.ReplacePreferencesRequest;
import com.foodmind.foodmindbackend.preference.api.request.UpdateCookingRegionRequest;
import com.foodmind.foodmindbackend.preference.api.response.UserPreferencesResponse;
import com.foodmind.foodmindbackend.preference.application.GetPreferences;
import com.foodmind.foodmindbackend.preference.application.ReplacePreferences;
import com.foodmind.foodmindbackend.preference.application.UpdateCookingRegion;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferenceController {

    private final GetPreferences getPreferences;
    private final ReplacePreferences replacePreferences;
    private final UpdateCookingRegion updateCookingRegion;

    public UserPreferenceController(
            GetPreferences getPreferences,
            ReplacePreferences replacePreferences,
            UpdateCookingRegion updateCookingRegion) {
        this.getPreferences = getPreferences;
        this.replacePreferences = replacePreferences;
        this.updateCookingRegion = updateCookingRegion;
    }

    @GetMapping
    UserPreferencesResponse get(@AuthenticationPrincipal FoodMindPrincipal principal) {
        return UserPreferencesResponse.from(getPreferences.get(principal.id()));
    }

    @PutMapping
    UserPreferencesResponse replace(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody ReplacePreferencesRequest request) {
        return UserPreferencesResponse.from(replacePreferences.replace(principal.id(), request.toReplacement()));
    }

    @PutMapping("/cooking-region")
    UserPreferencesResponse updateCookingRegion(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody UpdateCookingRegionRequest request) {
        return UserPreferencesResponse.from(
                updateCookingRegion.update(principal.id(), request.normalisedCookingRegion()));
    }
}
