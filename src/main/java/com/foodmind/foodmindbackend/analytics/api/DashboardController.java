package com.foodmind.foodmindbackend.analytics.api;

import com.foodmind.foodmindbackend.analytics.api.response.DashboardResponse;
import com.foodmind.foodmindbackend.analytics.application.GetDashboard;
import com.foodmind.foodmindbackend.analytics.domain.DashboardGroupBy;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: Owner-scoped dashboard metrics over a bounded local calendar range.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

@Validated
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final GetDashboard getDashboard;

    public DashboardController(GetDashboard getDashboard) {
        this.getDashboard = getDashboard;
    }

    @GetMapping
    public DashboardResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "DAY") DashboardGroupBy groupBy,
            @RequestParam(required = false) String timeZone) {
        return DashboardResponse.from(getDashboard.handle(principal.id(),
                new GetDashboard.Command(from, to, groupBy, timeZone)));
    }
}
