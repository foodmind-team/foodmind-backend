package com.foodmind.foodmindbackend.recommendation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record CleanlinessEvidence(
        BigDecimal score,
        OffsetDateTime observedAt,
        String sourceKind) {
}
