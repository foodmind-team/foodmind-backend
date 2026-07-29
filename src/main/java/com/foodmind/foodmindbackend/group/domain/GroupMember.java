package com.foodmind.foodmindbackend.group.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupMember(
        UUID membershipId,
        UUID groupId,
        UUID userId,
        String displayName,
        GroupRole role,
        MembershipStatus status,
        OffsetDateTime joinedAt,
        OffsetDateTime endedAt,
        long version) {
}
