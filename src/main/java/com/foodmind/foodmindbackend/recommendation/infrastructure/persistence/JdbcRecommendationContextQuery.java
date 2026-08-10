package com.foodmind.foodmindbackend.recommendation.infrastructure.persistence;

import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationContextQuery;
import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CleanlinessEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationContext;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

@Repository
public class JdbcRecommendationContextQuery implements RecommendationContextQuery {

    private static final int MAX_CANDIDATES = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRecommendationContextQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RecommendationContext load(UUID userId, RecommendationRequestContext request) {
        PreferenceEvidence preferences = preferences(userId);
        BigDecimal originLatitude = request.latitude() == null ? preferences.preferredLatitude() : request.latitude();
        BigDecimal originLongitude = request.longitude() == null ? preferences.preferredLongitude() : request.longitude();
        List<CandidateEvidence> catalogueCandidates = jdbcTemplate.query("""
                WITH active_group AS (
                    SELECT gm.group_id
                    FROM group_membership gm
                    JOIN trusted_group tg ON tg.id = gm.group_id
                    WHERE gm.user_id = :userId
                      AND gm.status = 'ACTIVE'
                      AND tg.status = 'ACTIVE'
                      AND (:groupProvided = false OR gm.group_id = :groupId)
                )
                SELECT pm.id AS place_meal_id,
                       m.id AS meal_id,
                       m.name AS meal_name,
                       m.meal_type,
                       c.code AS cuisine_code,
                       p.id AS place_id,
                       p.name AS place_name,
                       p.area,
                       p.latitude,
                       p.longitude,
                       pm.price,
                       pm.currency,
                       COALESCE(pm.spice_level, m.default_spice_level) AS spice_level,
                       pm.available,
                       po.score AS cleanliness_score,
                       po.observed_at AS cleanliness_observed_at,
                       po.source_kind AS cleanliness_source_kind,
                       COALESCE(dietary.codes, ARRAY[]::varchar[]) AS dietary_codes,
                       COALESCE(allergen.codes, ARRAY[]::varchar[]) AS allergen_codes,
                       EXISTS (
                           SELECT 1
                           FROM want_to_try wtt
                           WHERE wtt.user_id = :userId
                             AND wtt.deleted_at IS NULL
                             AND (
                                 (wtt.source_type = 'MEAL' AND wtt.meal_id = m.id)
                                 OR (wtt.source_type = 'PLACE' AND wtt.place_id = p.id)
                             )
                       ) AS want_to_try,
                       COALESCE(personal.record_count, 0) AS personal_record_count,
                       personal.average_rating AS personal_average_rating,
                       personal.last_record_at AS last_personal_record_at,
                       COALESCE(group_evidence.record_count, 0) AS group_record_count,
                       group_evidence.average_rating AS group_average_rating,
                       group_evidence.last_record_at AS last_group_record_at
                FROM place_meal pm
                JOIN meal m ON m.id = pm.meal_id
                JOIN cuisine c ON c.id = m.cuisine_id
                JOIN place p ON p.id = pm.place_id
                LEFT JOIN LATERAL (
                    SELECT score, observed_at, source_kind
                    FROM place_observation
                    WHERE place_id = p.id AND observation_type = 'CLEANLINESS'
                    ORDER BY observed_at DESC, id
                    LIMIT 1
                ) po ON true
                LEFT JOIN LATERAL (
                    SELECT array_agg(dt.code ORDER BY dt.code) AS codes
                    FROM meal_dietary_tag mdt
                    JOIN dietary_tag dt ON dt.id = mdt.dietary_tag_id
                    WHERE mdt.meal_id = m.id
                ) dietary ON true
                LEFT JOIN LATERAL (
                    SELECT array_agg(a.code ORDER BY a.code) AS codes
                    FROM meal_allergen ma
                    JOIN allergen a ON a.id = ma.allergen_id
                    WHERE ma.meal_id = m.id
                ) allergen ON true
                LEFT JOIN LATERAL (
                    SELECT count(*)::int AS record_count,
                           round(avg(fr.rating), 2) AS average_rating,
                           max(fr.occurred_at) AS last_record_at
                    FROM food_record fr
                    WHERE fr.owner_user_id = :userId
                      AND fr.deleted_at IS NULL
                      AND fr.meal_id = m.id
                      AND fr.place_id = p.id
                ) personal ON true
                LEFT JOIN LATERAL (
                    SELECT count(*)::int AS record_count,
                           round(avg(fr.rating), 2) AS average_rating,
                           max(fr.occurred_at) AS last_record_at
                    FROM food_record fr
                    WHERE fr.deleted_at IS NULL
                      AND fr.visibility = 'GROUP'
                      AND fr.meal_id = m.id
                      AND fr.place_id = p.id
                      AND EXISTS (
                          SELECT 1
                          FROM active_group
                          WHERE active_group.group_id = fr.group_id
                      )
                ) group_evidence ON true
                WHERE pm.available
                  AND m.curation_status = 'ACTIVE'
                  AND p.curation_status = 'ACTIVE'
                  AND (:mealType IS NULL OR m.meal_type = :mealType)
                ORDER BY m.meal_type, pm.price, pm.id
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("groupId", request.groupId())
                        .addValue("groupProvided", request.groupId() != null)
                        .addValue("mealType", request.mealType())
                        .addValue("limit", MAX_CANDIDATES),
                (rs, rowNum) -> candidateRow(rs, originLatitude, originLongitude));
        List<CandidateEvidence> recordCandidates = foodRecordCandidates(userId, request, originLatitude, originLongitude);
        Set<String> recordOfferings = new HashSet<>();
        for (CandidateEvidence record : recordCandidates) {
            if (record.mealId() != null && record.placeId() != null) {
                recordOfferings.add(record.mealId() + ":" + record.placeId());
            }
        }
        List<CandidateEvidence> candidates = new java.util.ArrayList<>(recordCandidates);
        catalogueCandidates.stream()
                .filter(candidate -> !recordOfferings.contains(candidate.mealId() + ":" + candidate.placeId()))
                .limit(MAX_CANDIDATES - candidates.size())
                .forEach(candidates::add);
        return new RecommendationContext(preferences, candidates);
    }

    /**
     * Food records are historical candidates.  The query deliberately returns only records the requester owns
     * or may already read through an active trusted group; it never broadens the record-detail permission model.
     */
    private List<CandidateEvidence> foodRecordCandidates(
            UUID userId,
            RecommendationRequestContext request,
            BigDecimal originLatitude,
            BigDecimal originLongitude) {
        return jdbcTemplate.query("""
                WITH active_group AS (
                    SELECT gm.group_id
                    FROM group_membership gm
                    JOIN trusted_group tg ON tg.id = gm.group_id
                    WHERE gm.user_id = :userId
                      AND gm.status = 'ACTIVE'
                      AND tg.status = 'ACTIVE'
                      AND (:groupProvided = false OR gm.group_id = :groupId)
                )
                SELECT fr.id AS food_record_id,
                       m.id AS meal_id,
                       COALESCE(m.name, fr.meal_name_snapshot) AS meal_name,
                       COALESCE(m.meal_type, 'RECORD') AS meal_type,
                       COALESCE(c.code, 'UNKNOWN') AS cuisine_code,
                       p.id AS place_id,
                       COALESCE(p.name, fr.place_name_snapshot) AS place_name,
                       p.area, p.latitude, p.longitude,
                       fr.price, fr.currency,
                       m.default_spice_level AS spice_level,
                       po.score AS cleanliness_score,
                       po.observed_at AS cleanliness_observed_at,
                       po.source_kind AS cleanliness_source_kind,
                       COALESCE(dietary.codes, ARRAY[]::varchar[]) AS dietary_codes,
                       COALESCE(allergen.codes, ARRAY[]::varchar[]) AS allergen_codes,
                       fr.rating,
                       fr.occurred_at,
                       CASE WHEN fr.owner_user_id = :userId THEN 1 ELSE 0 END AS personal_record_count,
                       CASE WHEN fr.owner_user_id = :userId THEN fr.rating ELSE NULL END AS personal_average_rating,
                       CASE WHEN fr.owner_user_id = :userId THEN fr.occurred_at ELSE NULL END AS last_personal_record_at,
                       CASE WHEN fr.owner_user_id = :userId THEN 0 ELSE 1 END AS group_record_count,
                       CASE WHEN fr.owner_user_id = :userId THEN NULL ELSE fr.rating END AS group_average_rating,
                       CASE WHEN fr.owner_user_id = :userId THEN NULL ELSE fr.occurred_at END AS last_group_record_at,
                       CASE WHEN fr.owner_user_id = :userId THEN 'Your record' ELSE u.display_name END AS record_owner_display_name
                FROM food_record fr
                JOIN app_user u ON u.id = fr.owner_user_id
                LEFT JOIN meal m ON m.id = fr.meal_id
                LEFT JOIN cuisine c ON c.id = COALESCE(m.cuisine_id, fr.cuisine_id)
                LEFT JOIN place p ON p.id = fr.place_id
                LEFT JOIN LATERAL (
                    SELECT score, observed_at, source_kind
                    FROM place_observation
                    WHERE place_id = p.id AND observation_type = 'CLEANLINESS'
                    ORDER BY observed_at DESC, id
                    LIMIT 1
                ) po ON true
                LEFT JOIN LATERAL (
                    SELECT array_agg(dt.code ORDER BY dt.code) AS codes
                    FROM meal_dietary_tag mdt JOIN dietary_tag dt ON dt.id = mdt.dietary_tag_id
                    WHERE mdt.meal_id = m.id
                ) dietary ON true
                LEFT JOIN LATERAL (
                    SELECT array_agg(a.code ORDER BY a.code) AS codes
                    FROM meal_allergen ma JOIN allergen a ON a.id = ma.allergen_id
                    WHERE ma.meal_id = m.id
                ) allergen ON true
                WHERE fr.deleted_at IS NULL
                  AND (fr.owner_user_id = :userId OR (
                        fr.visibility = 'GROUP' AND EXISTS (
                            SELECT 1 FROM active_group ag WHERE ag.group_id = fr.group_id)))
                  AND (:mealType IS NULL OR m.meal_type = :mealType)
                ORDER BY CASE WHEN fr.owner_user_id = :userId THEN 0 ELSE 1 END,
                         fr.would_eat_again DESC NULLS LAST, fr.rating DESC NULLS LAST,
                         fr.occurred_at DESC, fr.id
                LIMIT 50
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("groupId", request.groupId())
                        .addValue("groupProvided", request.groupId() != null)
                        .addValue("mealType", request.mealType()),
                (rs, rowNum) -> foodRecordCandidateRow(rs, originLatitude, originLongitude));
    }

    @Override
    public boolean activeGroupMember(UUID userId, UUID groupId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM group_membership gm
                JOIN trusted_group tg ON tg.id = gm.group_id
                WHERE gm.user_id = :userId
                  AND gm.group_id = :groupId
                  AND gm.status = 'ACTIVE'
                  AND tg.status = 'ACTIVE'
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("groupId", groupId),
                Integer.class);
        return count != null && count > 0;
    }

    private PreferenceEvidence preferences(UUID userId) {
        ScalarPreference scalar = jdbcTemplate.query("""
                SELECT budget_max, currency, spice_tolerance, preferred_area, preferred_latitude,
                       preferred_longitude, max_distance_km, minimum_cleanliness_evidence_score
                FROM user_preference
                WHERE user_id = :userId
                """,
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> new ScalarPreference(
                        rs.getBigDecimal("budget_max"),
                        rs.getString("currency").trim(),
                        getInteger(rs, "spice_tolerance"),
                        rs.getString("preferred_area"),
                        rs.getBigDecimal("preferred_latitude"),
                        rs.getBigDecimal("preferred_longitude"),
                        rs.getBigDecimal("max_distance_km"),
                        rs.getBigDecimal("minimum_cleanliness_evidence_score")))
                .stream()
                .findFirst()
                .orElse(new ScalarPreference(null, "SGD", null, null, null, null, null, null));
        return new PreferenceEvidence(
                scalar.budgetMax(),
                scalar.currency(),
                scalar.spiceTolerance(),
                scalar.preferredArea(),
                scalar.preferredLatitude(),
                scalar.preferredLongitude(),
                scalar.maxDistanceKm(),
                scalar.minimumCleanlinessEvidenceScore(),
                cuisineCodes(userId, "LIKE"),
                cuisineCodes(userId, "DISLIKE"),
                dietaryTagCodes(userId),
                allergenCodes(userId),
                preferredMealTypes(userId));
    }

    private List<String> cuisineCodes(UUID userId, String preference) {
        return jdbcTemplate.queryForList("""
                SELECT c.code
                FROM user_cuisine_preference ucp
                JOIN cuisine c ON c.id = ucp.cuisine_id
                WHERE ucp.user_id = :userId AND ucp.preference = :preference
                ORDER BY c.code
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("preference", preference),
                String.class);
    }

    private List<String> dietaryTagCodes(UUID userId) {
        return jdbcTemplate.queryForList("""
                SELECT dt.code
                FROM user_dietary_tag udt
                JOIN dietary_tag dt ON dt.id = udt.dietary_tag_id
                WHERE udt.user_id = :userId
                ORDER BY dt.code
                """,
                new MapSqlParameterSource("userId", userId),
                String.class);
    }

    private List<String> allergenCodes(UUID userId) {
        return jdbcTemplate.queryForList("""
                SELECT a.code
                FROM user_allergen ua
                JOIN allergen a ON a.id = ua.allergen_id
                WHERE ua.user_id = :userId
                ORDER BY a.code
                """,
                new MapSqlParameterSource("userId", userId),
                String.class);
    }

    private List<String> preferredMealTypes(UUID userId) {
        return jdbcTemplate.queryForList("""
                SELECT meal_type
                FROM user_preferred_meal_type
                WHERE user_id = :userId
                ORDER BY meal_type
                """,
                new MapSqlParameterSource("userId", userId),
                String.class);
    }

    private CandidateEvidence candidateRow(ResultSet rs, BigDecimal originLatitude, BigDecimal originLongitude) throws SQLException {
        BigDecimal latitude = rs.getBigDecimal("latitude");
        BigDecimal longitude = rs.getBigDecimal("longitude");
        CleanlinessEvidence cleanliness = cleanliness(rs);
        return new CandidateEvidence(
                rs.getObject("place_meal_id", UUID.class),
                rs.getObject("meal_id", UUID.class),
                rs.getString("meal_name"),
                rs.getString("meal_type"),
                rs.getString("cuisine_code"),
                rs.getObject("place_id", UUID.class),
                rs.getString("place_name"),
                rs.getString("area"),
                latitude,
                longitude,
                new MoneyAmount(rs.getBigDecimal("price"), rs.getString("currency").trim()),
                getInteger(rs, "spice_level"),
                rs.getBoolean("available"),
                cleanliness,
                stringArray(rs.getArray("dietary_codes")),
                stringArray(rs.getArray("allergen_codes")),
                rs.getBoolean("want_to_try"),
                rs.getInt("personal_record_count"),
                rs.getBigDecimal("personal_average_rating"),
                rs.getObject("last_personal_record_at", OffsetDateTime.class),
                rs.getInt("group_record_count"),
                rs.getBigDecimal("group_average_rating"),
                rs.getObject("last_group_record_at", OffsetDateTime.class),
                distanceKm(originLatitude, originLongitude, latitude, longitude));
    }

    private CandidateEvidence foodRecordCandidateRow(ResultSet rs, BigDecimal originLatitude, BigDecimal originLongitude)
            throws SQLException {
        BigDecimal latitude = rs.getBigDecimal("latitude");
        BigDecimal longitude = rs.getBigDecimal("longitude");
        BigDecimal priceAmount = rs.getBigDecimal("price");
        String currency = rs.getString("currency");
        return new CandidateEvidence(
                null, rs.getObject("meal_id", UUID.class), rs.getString("meal_name"), rs.getString("meal_type"),
                rs.getString("cuisine_code"), rs.getObject("place_id", UUID.class), rs.getString("place_name"),
                rs.getString("area"), latitude, longitude,
                priceAmount == null || currency == null ? null : new MoneyAmount(priceAmount, currency.trim()),
                getInteger(rs, "spice_level"), true, cleanliness(rs), stringArray(rs.getArray("dietary_codes")),
                stringArray(rs.getArray("allergen_codes")), false, rs.getInt("personal_record_count"),
                rs.getBigDecimal("personal_average_rating"), rs.getObject("last_personal_record_at", OffsetDateTime.class),
                rs.getInt("group_record_count"), rs.getBigDecimal("group_average_rating"),
                rs.getObject("last_group_record_at", OffsetDateTime.class), distanceKm(originLatitude, originLongitude, latitude, longitude),
                com.foodmind.foodmindbackend.recommendation.domain.CandidateSourceType.FOOD_RECORD,
                rs.getObject("food_record_id", UUID.class), rs.getString("record_owner_display_name"),
                rs.getObject("occurred_at", OffsetDateTime.class), true);
    }

    private CleanlinessEvidence cleanliness(ResultSet rs) throws SQLException {
        BigDecimal score = rs.getBigDecimal("cleanliness_score");
        if (rs.wasNull()) {
            return null;
        }
        return new CleanlinessEvidence(
                score,
                rs.getObject("cleanliness_observed_at", OffsetDateTime.class),
                rs.getString("cleanliness_source_kind"));
    }

    private List<String> stringArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String.class::cast).toList();
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal distanceKm(BigDecimal originLatitude, BigDecimal originLongitude, BigDecimal latitude, BigDecimal longitude) {
        if (originLatitude == null || originLongitude == null || latitude == null || longitude == null) {
            return null;
        }
        double lat1 = Math.toRadians(originLatitude.doubleValue());
        double lat2 = Math.toRadians(latitude.doubleValue());
        double deltaLat = Math.toRadians(latitude.subtract(originLatitude).doubleValue());
        double deltaLon = Math.toRadians(longitude.subtract(originLongitude).doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(6371.0 * c).setScale(3, RoundingMode.HALF_UP);
    }

    private record ScalarPreference(
            BigDecimal budgetMax,
            String currency,
            Integer spiceTolerance,
            String preferredArea,
            BigDecimal preferredLatitude,
            BigDecimal preferredLongitude,
            BigDecimal maxDistanceKm,
            BigDecimal minimumCleanlinessEvidenceScore) {
    }
}
