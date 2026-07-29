package com.foodmind.foodmindbackend.group.infrastructure.persistence;

import com.foodmind.foodmindbackend.group.application.port.GroupFeedQuery;
import com.foodmind.foodmindbackend.group.domain.FeedSourceType;
import com.foodmind.foodmindbackend.group.domain.GroupFeedCursor;
import com.foodmind.foodmindbackend.group.domain.GroupFeedEvent;
import com.foodmind.foodmindbackend.group.domain.GroupFeedPage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
public class GroupFeedQueryAdapter implements GroupFeedQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GroupFeedQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GroupFeedPage findVisibleEvents(UUID actorUserId, UUID groupId, GroupFeedCursor after, int limit) {
        int fetchLimit = limit + 1;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("groupId", groupId)
                .addValue("limit", fetchLimit)
                .addValue("afterOccurredAt", after == null ? null : after.occurredAt())
                .addValue("afterSourceType", after == null ? null : after.sourceType().name())
                .addValue("afterSourceId", after == null ? null : after.sourceId());
        List<GroupFeedEvent> rows = jdbcTemplate.query("""
                WITH authorised_group AS (
                    SELECT gm.group_id
                    FROM group_membership gm
                    JOIN trusted_group tg ON tg.id = gm.group_id
                    WHERE gm.user_id = :actorUserId
                      AND gm.group_id = :groupId
                      AND gm.status = 'ACTIVE'
                      AND tg.status = 'ACTIVE'
                ),
                events AS (
                    SELECT 'FOOD_RECORD' AS source_type,
                           fr.id AS source_id,
                           fr.occurred_at AS occurred_at,
                           fr.owner_user_id AS actor_user_id,
                           au.display_name AS actor_display_name,
                           fr.id AS food_record_id,
                           fr.meal_name_snapshot AS meal_name_snapshot,
                           NULL::uuid AS recommendation_share_id,
                           NULL::uuid AS recommendation_candidate_id,
                           NULL::text AS message
                    FROM food_record fr
                    JOIN authorised_group ag ON ag.group_id = fr.group_id
                    JOIN app_user au ON au.id = fr.owner_user_id
                    WHERE fr.visibility = 'GROUP'
                      AND fr.deleted_at IS NULL
                    UNION ALL
                    SELECT 'RECOMMENDATION_SHARE' AS source_type,
                           grs.id AS source_id,
                           grs.created_at AS occurred_at,
                           grs.shared_by_user_id AS actor_user_id,
                           au.display_name AS actor_display_name,
                           NULL::uuid AS food_record_id,
                           NULL::varchar AS meal_name_snapshot,
                           grs.id AS recommendation_share_id,
                           grs.recommendation_candidate_id AS recommendation_candidate_id,
                           grs.message AS message
                    FROM group_recommendation_share grs
                    JOIN authorised_group ag ON ag.group_id = grs.group_id
                    JOIN app_user au ON au.id = grs.shared_by_user_id
                    WHERE grs.deleted_at IS NULL
                )
                SELECT *
                FROM events
                WHERE CAST(:afterOccurredAt AS timestamptz) IS NULL
                   OR occurred_at < CAST(:afterOccurredAt AS timestamptz)
                   OR (
                        occurred_at = CAST(:afterOccurredAt AS timestamptz)
                        AND (
                            source_type > CAST(:afterSourceType AS text)
                            OR (source_type = CAST(:afterSourceType AS text) AND source_id < CAST(:afterSourceId AS uuid))
                        )
                   )
                ORDER BY occurred_at DESC, source_type ASC, source_id DESC
                LIMIT :limit
                """,
                params,
                this::eventRow);
        String nextCursor = null;
        List<GroupFeedEvent> items = rows;
        if (rows.size() > limit) {
            items = new ArrayList<>(rows.subList(0, limit));
            GroupFeedEvent last = items.get(items.size() - 1);
            nextCursor = new GroupFeedCursor(last.occurredAt(), last.sourceType(), last.sourceId()).encode();
        }
        return new GroupFeedPage(items, nextCursor);
    }

    private GroupFeedEvent eventRow(ResultSet rs, int rowNum) throws SQLException {
        return new GroupFeedEvent(
                FeedSourceType.valueOf(rs.getString("source_type")),
                rs.getObject("source_id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("actor_display_name"),
                rs.getObject("food_record_id", UUID.class),
                rs.getString("meal_name_snapshot"),
                rs.getObject("recommendation_share_id", UUID.class),
                rs.getObject("recommendation_candidate_id", UUID.class),
                rs.getString("message"));
    }
}
