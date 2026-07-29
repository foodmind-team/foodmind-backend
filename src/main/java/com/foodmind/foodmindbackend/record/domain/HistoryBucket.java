package com.foodmind.foodmindbackend.record.domain;

import java.time.LocalDate;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public record HistoryBucket(
        LocalDate bucketStart,
        long totalCount,
        long foodCount,
        long drinkCount) {
}
