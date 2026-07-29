package com.foodmind.foodmindbackend.group.application.port;

import com.foodmind.foodmindbackend.group.domain.GroupInvitation;
import com.foodmind.foodmindbackend.group.domain.GroupMember;
import com.foodmind.foodmindbackend.group.domain.TrustedGroup;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public interface GroupRepository {

    TrustedGroup create(UUID actorUserId, String name, String description);

    List<TrustedGroup> listForMember(UUID actorUserId);

    Optional<TrustedGroup> findForMember(UUID actorUserId, UUID groupId);

    Optional<TrustedGroup> findActiveForOwner(UUID actorUserId, UUID groupId);

    TrustedGroup update(UUID groupId, String name, String description);

    TrustedGroup archive(UUID groupId);

    GroupInvitation createInvitation(UUID actorUserId, UUID groupId, String tokenHash, OffsetDateTime expiresAt, int maxUses, String rawToken);

    GroupMember joinByTokenHash(UUID actorUserId, String tokenHash, OffsetDateTime now);

    List<GroupMember> listMembers(UUID groupId);

    void removeMember(UUID groupId, UUID userId, String terminalStatus);
}
