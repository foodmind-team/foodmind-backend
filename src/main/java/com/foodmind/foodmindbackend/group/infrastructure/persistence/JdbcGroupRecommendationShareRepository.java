package com.foodmind.foodmindbackend.group.infrastructure.persistence;

import com.foodmind.foodmindbackend.group.application.port.GroupRecommendationShareRepository;
import com.foodmind.foodmindbackend.group.domain.GroupRecommendationShare;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
public class JdbcGroupRecommendationShareRepository implements GroupRecommendationShareRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcGroupRecommendationShareRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean candidateOwnedBy(UUID actorUserId, UUID candidateId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM recommendation_candidate rc
                JOIN recommendation_session rs ON rs.id = rc.session_id
                WHERE rc.id = :candidateId
                  AND rs.user_id = :actorUserId
                """,
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("actorUserId", actorUserId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public GroupRecommendationShare share(UUID actorUserId, UUID groupId, UUID candidateId, String message) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO group_recommendation_share (
                    id, group_id, shared_by_user_id, recommendation_candidate_id, message
                )
                VALUES (:id, :groupId, :actorUserId, :candidateId, :message)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("groupId", groupId)
                        .addValue("actorUserId", actorUserId)
                        .addValue("candidateId", candidateId)
                        .addValue("message", message));
        return jdbcTemplate.query("""
                SELECT id, group_id, shared_by_user_id, recommendation_candidate_id, message, created_at
                FROM group_recommendation_share
                WHERE id = :id
                """,
                new MapSqlParameterSource("id", id),
                this::shareRow)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private GroupRecommendationShare shareRow(ResultSet rs, int rowNum) throws SQLException {
        return new GroupRecommendationShare(
                rs.getObject("id", UUID.class),
                rs.getObject("group_id", UUID.class),
                rs.getObject("shared_by_user_id", UUID.class),
                rs.getObject("recommendation_candidate_id", UUID.class),
                rs.getString("message"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
