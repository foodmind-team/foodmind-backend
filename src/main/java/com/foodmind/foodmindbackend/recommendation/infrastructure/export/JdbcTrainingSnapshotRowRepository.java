package com.foodmind.foodmindbackend.recommendation.infrastructure.export;

import com.foodmind.foodmindbackend.recommendation.application.TrainingSnapshotSourceRow;
import com.foodmind.foodmindbackend.recommendation.application.port.TrainingSnapshotRowRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@Repository
public class JdbcTrainingSnapshotRowRepository implements TrainingSnapshotRowRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTrainingSnapshotRowRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TrainingSnapshotSourceRow> rows(
            OffsetDateTime decisionFrom,
            OffsetDateTime decisionTo,
            OffsetDateTime observedThrough) {
        return jdbcTemplate.query("""
                SELECT user_id, raw_offering_id, raw_meal_id, explicit_label, decision_created_at,
                       later_rating, later_rating_created_at, would_eat_again, would_eat_again_created_at,
                       candidate_rank, candidate_type, feature_schema_version, raw_feature_snapshot,
                       model_version, model_status, fallback_version, fallback_status
                FROM public.foodmind_ml_interaction_export_rows_v1(:decisionFrom, :decisionTo, :observedThrough)
                ORDER BY decision_created_at ASC, user_id ASC, raw_offering_id ASC
                """,
                new MapSqlParameterSource()
                        .addValue("decisionFrom", decisionFrom)
                        .addValue("decisionTo", decisionTo)
                        .addValue("observedThrough", observedThrough),
                this::row);
    }

    private TrainingSnapshotSourceRow row(ResultSet rs, int rowNum) throws SQLException {
        return new TrainingSnapshotSourceRow(
                rs.getObject("user_id", UUID.class),
                rs.getObject("raw_offering_id", UUID.class),
                rs.getObject("raw_meal_id", UUID.class),
                rs.getInt("explicit_label"),
                rs.getObject("decision_created_at", OffsetDateTime.class),
                rs.getBigDecimal("later_rating"),
                rs.getObject("later_rating_created_at", OffsetDateTime.class),
                (Boolean) rs.getObject("would_eat_again"),
                rs.getObject("would_eat_again_created_at", OffsetDateTime.class),
                (Integer) rs.getObject("candidate_rank"),
                rs.getString("candidate_type"),
                rs.getString("feature_schema_version"),
                rs.getString("raw_feature_snapshot"),
                rs.getString("model_version"),
                rs.getString("model_status"),
                rs.getString("fallback_version"),
                rs.getString("fallback_status"));
    }
}
