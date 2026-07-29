package com.foodmind.foodmindbackend.group.application;

import com.foodmind.foodmindbackend.group.application.port.GroupRepository;
import com.foodmind.foodmindbackend.group.domain.GroupInvitation;
import com.foodmind.foodmindbackend.group.domain.GroupMember;
import com.foodmind.foodmindbackend.group.domain.GroupValidation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@Service
public class GroupInvitationService {

    private final GroupRepository groupRepository;
    private final GroupMembershipPolicy membershipPolicy;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public GroupInvitationService(GroupRepository groupRepository, GroupMembershipPolicy membershipPolicy, Clock clock) {
        this.groupRepository = groupRepository;
        this.membershipPolicy = membershipPolicy;
        this.secureRandom = new SecureRandom();
        this.clock = clock;
    }

    @Transactional
    public GroupInvitation create(UUID actorUserId, UUID groupId, Command command) {
        membershipPolicy.requireOwner(actorUserId, groupId);
        int maxUses = command.maxUses() == null ? 1 : command.maxUses();
        Duration ttl = command.expiresInMinutes() == null
                ? Duration.ofHours(command.expiresInHours() == null ? 72 : command.expiresInHours())
                : Duration.ofMinutes(command.expiresInMinutes());
        GroupValidation.validateInvitation(ttl, maxUses);
        String rawToken = newRawToken();
        return groupRepository.createInvitation(actorUserId, groupId, hash(rawToken), OffsetDateTime.now(clock).plus(ttl), maxUses, rawToken);
    }

    @Transactional
    public GroupMember join(UUID actorUserId, String rawToken) {
        return groupRepository.joinByTokenHash(actorUserId, hash(rawToken == null ? "" : rawToken.trim()), OffsetDateTime.now(clock));
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record Command(Integer expiresInMinutes, Integer expiresInHours, Integer maxUses) {
    }
}
