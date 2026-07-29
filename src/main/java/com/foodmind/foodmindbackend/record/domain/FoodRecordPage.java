package com.foodmind.foodmindbackend.record.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

public record FoodRecordPage(
        List<FoodRecord> items,
        long totalItems) {

    public FoodRecordPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
