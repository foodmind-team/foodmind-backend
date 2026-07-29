package com.foodmind.foodmindbackend.catalog.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record PlaceObservation(
        UUID id,
        String observationType,
        BigDecimal score,
        String note,
        String sourceKind,
        OffsetDateTime observedAt) {
}
