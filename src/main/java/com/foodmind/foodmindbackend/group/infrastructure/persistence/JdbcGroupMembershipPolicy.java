package com.foodmind.foodmindbackend.group.infrastructure.persistence;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.group.application.GroupMembershipPolicy;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@Component
public class JdbcGroupMembershipPolicy implements GroupMembershipPolicy {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcGroupMembershipPolicy(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void requireActiveMember(UUID actorUserId, UUID groupId) {
        if (!exists("""
                SELECT count(*)
                FROM group_membership gm
                JOIN trusted_group tg ON tg.id = gm.group_id
                WHERE gm.user_id = :actorUserId
                  AND gm.group_id = :groupId
                  AND gm.status = 'ACTIVE'
                  AND tg.status = 'ACTIVE'
                """, actorUserId, groupId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public void requireOwner(UUID actorUserId, UUID groupId) {
        if (!exists("""
                SELECT count(*)
                FROM group_membership gm
                JOIN trusted_group tg ON tg.id = gm.group_id
                WHERE gm.user_id = :actorUserId
                  AND gm.group_id = :groupId
                  AND gm.role = 'OWNER'
                  AND gm.status = 'ACTIVE'
                  AND tg.status = 'ACTIVE'
                """, actorUserId, groupId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public void assertLastOwnerRetained(UUID groupId, UUID leavingOrRemovedUserId) {
        Integer owners = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM group_membership
                WHERE group_id = :groupId
                  AND role = 'OWNER'
                  AND status = 'ACTIVE'
                  AND user_id <> :actorUserId
                """,
                params(leavingOrRemovedUserId, groupId),
                Integer.class);
        if (owners == null || owners == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "A group must retain an active owner.");
        }
    }

    private boolean exists(String sql, UUID actorUserId, UUID groupId) {
        Integer count = jdbcTemplate.queryForObject(sql, params(actorUserId, groupId), Integer.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource params(UUID actorUserId, UUID groupId) {
        return new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("groupId", groupId);
    }
}
