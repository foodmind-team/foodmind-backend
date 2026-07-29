package com.foodmind.foodmindbackend.record.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record DrinkRecordPage(
        List<DrinkRecord> items,
        long totalItems) {
}
