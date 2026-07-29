package com.foodmind.foodmindbackend.group.api.response;

import com.foodmind.foodmindbackend.group.domain.GroupStatus;
import com.foodmind.foodmindbackend.group.domain.TrustedGroup;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupResponse(
        UUID id,
        String name,
        String description,
        UUID createdByUserId,
        GroupStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {

    public static GroupResponse from(TrustedGroup group) {
        return new GroupResponse(
                group.id(),
                group.name(),
                group.description(),
                group.createdByUserId(),
                group.status(),
                group.createdAt(),
                group.updatedAt(),
                group.version());
    }
}
