package com.foodmind.foodmindbackend.record.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.record.api.response.HistoryResponse;
import com.foodmind.foodmindbackend.record.application.GetHistory;
import com.foodmind.foodmindbackend.record.domain.HistoryPeriod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

@Validated
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final GetHistory getHistory;

    public HistoryController(GetHistory getHistory) {
        this.getHistory = getHistory;
    }

    @GetMapping
    HistoryResponse getHistory(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "DAY") HistoryPeriod period,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID cuisineId,
            @RequestParam(required = false) UUID placeId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        return HistoryResponse.from(getHistory.handle(principal.id(), new GetHistory.Command(
                from,
                to,
                period,
                types,
                timeZone,
                groupId,
                cuisineId,
                placeId,
                cursor,
                size)));
    }
}
