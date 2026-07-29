package com.foodmind.foodmindbackend.group.infrastructure.persistence;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.group.application.port.GroupRepository;
import com.foodmind.foodmindbackend.group.domain.GroupInvitation;
import com.foodmind.foodmindbackend.group.domain.GroupMember;
import com.foodmind.foodmindbackend.group.domain.GroupRole;
import com.foodmind.foodmindbackend.group.domain.GroupStatus;
import com.foodmind.foodmindbackend.group.domain.MembershipStatus;
import com.foodmind.foodmindbackend.group.domain.TrustedGroup;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@Repository
public class JdbcGroupRepository implements GroupRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcGroupRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TrustedGroup create(UUID actorUserId, String name, String description) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO trusted_group (id, name, description, created_by_user_id)
                VALUES (:id, :name, :description, :actorUserId)
                """,
                new MapSqlParameterSource()
                        .addValue("id", groupId)
                        .addValue("name", name)
                        .addValue("description", description)
                        .addValue("actorUserId", actorUserId));
        jdbcTemplate.update("""
                INSERT INTO group_membership (id, group_id, user_id, role, status, joined_at)
                VALUES (:id, :groupId, :actorUserId, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("groupId", groupId)
                        .addValue("actorUserId", actorUserId));
        return findForMember(actorUserId, groupId).orElseThrow();
    }

    @Override
    public List<TrustedGroup> listForMember(UUID actorUserId) {
        return jdbcTemplate.query("""
                SELECT tg.id, tg.name, tg.description, tg.created_by_user_id, tg.status,
                       tg.created_at, tg.updated_at, tg.version
                FROM trusted_group tg
                JOIN group_membership gm ON gm.group_id = tg.id
                WHERE gm.user_id = :actorUserId
                  AND gm.status = 'ACTIVE'
                ORDER BY tg.created_at DESC, tg.id ASC
                """,
                new MapSqlParameterSource("actorUserId", actorUserId),
                this::groupRow);
    }

    @Override
    public Optional<TrustedGroup> findForMember(UUID actorUserId, UUID groupId) {
        return jdbcTemplate.query("""
                SELECT tg.id, tg.name, tg.description, tg.created_by_user_id, tg.status,
                       tg.created_at, tg.updated_at, tg.version
                FROM trusted_group tg
                JOIN group_membership gm ON gm.group_id = tg.id
                WHERE tg.id = :groupId
                  AND gm.user_id = :actorUserId
                  AND gm.status = 'ACTIVE'
                """,
                params(actorUserId, groupId),
                this::groupRow)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<TrustedGroup> findActiveForOwner(UUID actorUserId, UUID groupId) {
        return jdbcTemplate.query("""
                SELECT tg.id, tg.name, tg.description, tg.created_by_user_id, tg.status,
                       tg.created_at, tg.updated_at, tg.version
                FROM trusted_group tg
                JOIN group_membership gm ON gm.group_id = tg.id
                WHERE tg.id = :groupId
                  AND tg.status = 'ACTIVE'
                  AND gm.user_id = :actorUserId
                  AND gm.role = 'OWNER'
                  AND gm.status = 'ACTIVE'
                """,
                params(actorUserId, groupId),
                this::groupRow)
                .stream()
                .findFirst();
    }

    @Override
    public TrustedGroup update(UUID groupId, String name, String description) {
        jdbcTemplate.update("""
                UPDATE trusted_group
                SET name = :name,
                    description = :description,
                    version = version + 1
                WHERE id = :groupId
                  AND status = 'ACTIVE'
                """,
                new MapSqlParameterSource()
                        .addValue("groupId", groupId)
                        .addValue("name", name)
                        .addValue("description", description));
        return findById(groupId).orElseThrow();
    }

    @Override
    public TrustedGroup archive(UUID groupId) {
        jdbcTemplate.update("""
                UPDATE trusted_group
                SET status = 'ARCHIVED',
                    version = version + 1
                WHERE id = :groupId
                  AND status = 'ACTIVE'
                """,
                new MapSqlParameterSource("groupId", groupId));
        return findById(groupId).orElseThrow();
    }

    @Override
    public GroupInvitation createInvitation(
            UUID actorUserId,
            UUID groupId,
            String tokenHash,
            OffsetDateTime expiresAt,
            int maxUses,
            String rawToken) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO group_invitation (id, group_id, token_hash, created_by_user_id, expires_at, max_uses)
                VALUES (:id, :groupId, :tokenHash, :actorUserId, :expiresAt, :maxUses)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("groupId", groupId)
                        .addValue("tokenHash", tokenHash)
                        .addValue("actorUserId", actorUserId)
                        .addValue("expiresAt", expiresAt)
                        .addValue("maxUses", maxUses));
        return new GroupInvitation(id, groupId, rawToken, expiresAt, maxUses, 0, "ACTIVE", null);
    }

    @Override
    public GroupMember joinByTokenHash(UUID actorUserId, String tokenHash, OffsetDateTime now) {
        InvitationState invitation = jdbcTemplate.query("""
                SELECT gi.id, gi.group_id, gi.expires_at, gi.max_uses, gi.use_count, gi.status, tg.status AS group_status
                FROM group_invitation gi
                JOIN trusted_group tg ON tg.id = gi.group_id
                WHERE gi.token_hash = :tokenHash
                FOR UPDATE OF gi
                """,
                new MapSqlParameterSource("tokenHash", tokenHash),
                this::invitationStateRow)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"ACTIVE".equals(invitation.status()) || !"ACTIVE".equals(invitation.groupStatus()) || !invitation.expiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (invitation.useCount() >= invitation.maxUses()) {
            throw new ApiException(ErrorCode.CONFLICT, "Invitation has no remaining uses.");
        }

        Optional<GroupMember> existing = findMember(invitation.groupId(), actorUserId);
        if (existing.isPresent() && existing.get().status() == MembershipStatus.ACTIVE) {
            return existing.get();
        }

        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE group_membership
                    SET role = 'MEMBER',
                        status = 'ACTIVE',
                        joined_at = :now,
                        ended_at = NULL,
                        version = version + 1
                    WHERE group_id = :groupId
                      AND user_id = :actorUserId
                    """, params(actorUserId, invitation.groupId()).addValue("now", now));
        } else {
            jdbcTemplate.update("""
                    INSERT INTO group_membership (id, group_id, user_id, role, status, joined_at)
                    VALUES (:id, :groupId, :actorUserId, 'MEMBER', 'ACTIVE', :now)
                    """,
                    params(actorUserId, invitation.groupId())
                            .addValue("id", UUID.randomUUID())
                            .addValue("now", now));
        }

        int nextUseCount = invitation.useCount() + 1;
        jdbcTemplate.update("""
                UPDATE group_invitation
                SET use_count = :useCount,
                    status = :status
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("useCount", nextUseCount)
                        .addValue("status", nextUseCount >= invitation.maxUses() ? "EXHAUSTED" : "ACTIVE")
                        .addValue("id", invitation.id()));
        return findMember(invitation.groupId(), actorUserId).orElseThrow();
    }

    @Override
    public List<GroupMember> listMembers(UUID groupId) {
        return jdbcTemplate.query("""
                SELECT gm.id, gm.group_id, gm.user_id, au.display_name, gm.role, gm.status,
                       gm.joined_at, gm.ended_at, gm.version
                FROM group_membership gm
                JOIN app_user au ON au.id = gm.user_id
                WHERE gm.group_id = :groupId
                  AND gm.status = 'ACTIVE'
                ORDER BY gm.role ASC, gm.joined_at ASC, gm.user_id ASC
                """,
                new MapSqlParameterSource("groupId", groupId),
                this::memberRow);
    }

    @Override
    public void removeMember(UUID groupId, UUID userId, String terminalStatus) {
        jdbcTemplate.update("""
                UPDATE group_membership
                SET status = :status,
                    ended_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE group_id = :groupId
                  AND user_id = :userId
                  AND status = 'ACTIVE'
                """,
                new MapSqlParameterSource()
                        .addValue("groupId", groupId)
                        .addValue("userId", userId)
                        .addValue("status", terminalStatus));
    }

    private Optional<TrustedGroup> findById(UUID groupId) {
        return jdbcTemplate.query("""
                SELECT id, name, description, created_by_user_id, status, created_at, updated_at, version
                FROM trusted_group
                WHERE id = :groupId
                """,
                new MapSqlParameterSource("groupId", groupId),
                this::groupRow)
                .stream()
                .findFirst();
    }

    private Optional<GroupMember> findMember(UUID groupId, UUID userId) {
        return jdbcTemplate.query("""
                SELECT gm.id, gm.group_id, gm.user_id, au.display_name, gm.role, gm.status,
                       gm.joined_at, gm.ended_at, gm.version
                FROM group_membership gm
                JOIN app_user au ON au.id = gm.user_id
                WHERE gm.group_id = :groupId
                  AND gm.user_id = :actorUserId
                """,
                params(userId, groupId),
                this::memberRow)
                .stream()
                .findFirst();
    }

    private TrustedGroup groupRow(ResultSet rs, int rowNum) throws SQLException {
        return new TrustedGroup(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("created_by_user_id", UUID.class),
                GroupStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getLong("version"));
    }

    private GroupMember memberRow(ResultSet rs, int rowNum) throws SQLException {
        return new GroupMember(
                rs.getObject("id", UUID.class),
                rs.getObject("group_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                GroupRole.valueOf(rs.getString("role")),
                MembershipStatus.valueOf(rs.getString("status")),
                rs.getObject("joined_at", OffsetDateTime.class),
                rs.getObject("ended_at", OffsetDateTime.class),
                rs.getLong("version"));
    }

    private InvitationState invitationStateRow(ResultSet rs, int rowNum) throws SQLException {
        return new InvitationState(
                rs.getObject("id", UUID.class),
                rs.getObject("group_id", UUID.class),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getInt("max_uses"),
                rs.getInt("use_count"),
                rs.getString("status"),
                rs.getString("group_status"));
    }

    private MapSqlParameterSource params(UUID actorUserId, UUID groupId) {
        return new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("groupId", groupId);
    }

    private record InvitationState(
            UUID id,
            UUID groupId,
            OffsetDateTime expiresAt,
            int maxUses,
            int useCount,
            String status,
            String groupStatus) {
    }
}
