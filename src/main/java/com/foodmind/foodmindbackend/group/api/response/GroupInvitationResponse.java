package com.foodmind.foodmindbackend.group.api.response;

import com.foodmind.foodmindbackend.group.domain.GroupInvitation;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupInvitationResponse(
        UUID id,
        UUID groupId,
        String token,
        OffsetDateTime expiresAt,
        int maxUses,
        int useCount,
        String status) {

    public static GroupInvitationResponse from(GroupInvitation invitation) {
        return new GroupInvitationResponse(
                invitation.id(),
                invitation.groupId(),
                invitation.rawToken(),
                invitation.expiresAt(),
                invitation.maxUses(),
                invitation.useCount(),
                invitation.status());
    }
}
