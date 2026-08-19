package com.foodmind.foodmindbackend.analytics.api;

import com.foodmind.foodmindbackend.analytics.api.response.WeeklyRecapResponse;
import com.foodmind.foodmindbackend.analytics.application.GetWeeklyRecap;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: Owner-local Monday weekly recap endpoint.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:35 pm
 */

@RestController
@RequestMapping("/api/v1/weekly-recaps")
public class WeeklyRecapController {

    private final GetWeeklyRecap getWeeklyRecap;

    public WeeklyRecapController(GetWeeklyRecap getWeeklyRecap) {
        this.getWeeklyRecap = getWeeklyRecap;
    }



    @GetMapping("/{weekStart}")
    public WeeklyRecapResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable String weekStart) {
        return WeeklyRecapResponse.from(getWeeklyRecap.handle(principal.id(), weekStart));
    }
}
