package com.foodmind.foodmindbackend.group.api.response;

import com.foodmind.foodmindbackend.group.domain.GroupMember;
import com.foodmind.foodmindbackend.group.domain.GroupRole;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupMemberResponse(
        UUID userId,
        String displayName,
        GroupRole role,
        OffsetDateTime joinedAt) {

    public static GroupMemberResponse from(GroupMember member) {
        return new GroupMemberResponse(member.userId(), member.displayName(), member.role(), member.joinedAt());
    }
}
