package com.foodmind.foodmindbackend.group.application;

import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public interface GroupMembershipPolicy {

    void requireActiveMember(UUID actorUserId, UUID groupId);

    void requireOwner(UUID actorUserId, UUID groupId);

    void assertLastOwnerRetained(UUID groupId, UUID leavingOrRemovedUserId);
}
