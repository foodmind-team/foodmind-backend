package com.foodmind.foodmindbackend.wanttotry.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record WantToTrySourceSummary(
        String title,
        String subtitle,
        String snippet,
        String imageReference,
        String visibility,
        UUID ownerUserId,
        UUID groupId,
        OffsetDateTime occurredAt) {
}
