package com.foodmind.foodmindbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FoodmindBackendApplicationTests extends PostgreSqlContainerSupport {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @BeforeEach
    void cleanMutableFixtureTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    recommendation_feedback,
                    recommendation_candidate,
                    recommendation_session,
                    auth_session,
                    chat_reference,
                    chat_session,
                    audit_event,
                    user_preference,
                    app_user
                CASCADE
                """);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void flywayMigratesCurrentBackendMigrationsSuccessfullyInOrder() {
        MigrationInfo[] migrations = flyway.info().applied();

        assertThat(migrations)
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19");
        assertThat(migrations)
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
    }

    @Test
    void databaseSchemaMatchesFoundationContract() {
        assertThat(count("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """)).isEqualTo(65);
        assertThat(count("""
                SELECT count(*)
                FROM information_schema.views
                WHERE table_schema = 'public'
                  AND table_name LIKE 'analytics_%'
                """)).isEqualTo(10);
        assertThat(count("""
                SELECT count(*)
                FROM information_schema.views
                WHERE table_schema = 'public'
                  AND table_name = 'ml_interaction_export_source_v1'
                """)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'")).isEqualTo(1);
        assertThat(count("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'search_vector'
                  AND table_name IN ('place', 'food_product', 'food_record')
                """)).isEqualTo(3);
        assertThat(count("""
                SELECT count(*)
                FROM information_schema.role_table_grants
                WHERE table_schema = 'public'
                  AND table_name = 'ml_interaction_export_source_v1'
                  AND grantee = 'PUBLIC'
                """)).isZero();
    }

    @Test
    void deterministicCatalogueSeedMatchesContract() {
        assertThat(queryForList("SELECT code FROM cuisine ORDER BY code"))
                .containsExactly("CHINESE", "INDIAN", "JAPANESE", "MALAY", "SINGAPOREAN");
        assertThat(queryForList("SELECT code FROM dietary_tag ORDER BY code"))
                .containsExactly("VEGAN", "VEGETARIAN");
        assertThat(count("SELECT count(*) FROM allergen")).isEqualTo(9);
        assertThat(count("SELECT count(*) FROM meal")).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM place")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM place_meal")).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM food_product")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM recipe")).isEqualTo(3);
    }

    @Test
    void lowerBoundAndCoordinateConstraintsRejectUnsafeValues() {
        UUID userId = createUser("constraints");

        assertSqlRejected("""
                INSERT INTO user_preference (user_id, budget_min)
                VALUES (?, 'NaN'::numeric)
                """, userId);
        assertSqlRejected("""
                INSERT INTO user_preference (user_id, preferred_latitude)
                VALUES (?, 1.234567)
                """, userId);
        assertSqlRejected("""
                INSERT INTO user_preference (user_id, max_distance_km)
                VALUES (?, 1.00)
                """, userId);
        assertSqlRejected("""
                INSERT INTO recommendation_session (
                    id, user_id, latitude, request_context, public_contract_version, correlation_id
                )
                VALUES (?, ?, 1.234567, '{}'::jsonb, 'public-v1', ?)
                """, UUID.randomUUID(), userId, UUID.randomUUID());
        assertSqlRejected("""
                INSERT INTO recommendation_session (
                    id, user_id, max_distance_km, request_context, public_contract_version, correlation_id
                )
                VALUES (?, ?, 2.00, '{}'::jsonb, 'public-v1', ?)
                """, UUID.randomUUID(), userId, UUID.randomUUID());
    }

    @Test
    void refreshSessionReplacementCannotCrossUserOrTokenFamily() {
        UUID firstUserId = createUser("session-one");
        UUID secondUserId = createUser("session-two");
        UUID firstFamilyId = UUID.randomUUID();
        UUID secondFamilyId = UUID.randomUUID();
        UUID firstSessionId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();

        insertAuthSession(firstSessionId, firstUserId, firstFamilyId, "0".repeat(64));
        insertAuthSession(secondSessionId, secondUserId, secondFamilyId, "1".repeat(64));

        assertSqlRejected("""
                UPDATE auth_session
                SET rotated_at = CURRENT_TIMESTAMP,
                    replaced_by_session_id = ?
                WHERE id = ?
                """, secondSessionId, firstSessionId);
    }

    @Test
    void recommendationCandidateShapeConstraintsRejectIncompleteEvidence() {
        UUID userId = createUser("candidate");
        UUID sessionId = createProcessingRecommendationSession(userId, OffsetDateTime.parse("2026-07-28T02:00:00Z"));
        UUID placeMealId = jdbcTemplate.queryForObject("SELECT id FROM place_meal ORDER BY id LIMIT 1", UUID.class);

        assertSqlRejected("""
                INSERT INTO recommendation_candidate (
                    id, session_id, place_meal_id, eligibility_status, evidence_snapshot
                )
                VALUES (?, ?, ?, 'RETURNED', '{}'::jsonb)
                """, UUID.randomUUID(), sessionId, placeMealId);
        assertSqlRejected("""
                INSERT INTO recommendation_candidate (
                    id, session_id, place_meal_id, eligibility_status, feature_schema_version,
                    candidate_type, rank, evidence_snapshot
                )
                VALUES (?, ?, ?, 'RETURNED', 'features-v1', 'PERSONAL', 1, '{}'::jsonb)
                """, UUID.randomUUID(), sessionId, placeMealId);

        jdbcTemplate.update("""
                INSERT INTO recommendation_candidate (
                    id, session_id, place_meal_id, eligibility_status, feature_schema_version,
                    feature_snapshot, candidate_type, rank, evidence_snapshot
                )
                VALUES (?, ?, ?, 'RETURNED', 'features-v1', '{}'::jsonb, 'PERSONAL', 1, '{}'::jsonb)
                """, UUID.randomUUID(), sessionId, placeMealId);

        assertThat(count("SELECT count(*) FROM recommendation_candidate WHERE session_id = ?", sessionId)).isEqualTo(1);
    }

    @Test
    void appendOnlyTablesRejectMutation() {
        UUID auditEventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO audit_event (
                    id, action, resource_type, resource_id, outcome, correlation_id, safe_metadata
                )
                VALUES (?, 'TEST_ACTION', 'TEST_RESOURCE', ?, 'SUCCEEDED', ?, '{}'::jsonb)
                """, auditEventId, UUID.randomUUID(), UUID.randomUUID());

        assertSqlRejected("UPDATE audit_event SET outcome = 'FAILED' WHERE id = ?", auditEventId);
    }

    @Test
    void searchExploreChatAndMlFixturesExerciseFoundationViewsAndFunctions() {
        UUID userId = createUser("functions");

        assertThat(count("""
                SELECT count(*)
                FROM public.foodmind_search_documents_for_user(
                    ?, 'soy', ARRAY['FOOD_PRODUCT']::varchar[], 10, NULL, NULL, NULL, NULL
                )
                """, userId)).isGreaterThan(0);
        assertThat(count("""
                SELECT count(*)
                FROM public.foodmind_explore_documents_for_user(
                    ?, ARRAY['PLACE']::varchar[], 10, NULL, NULL, NULL
                )
                """, userId)).isEqualTo(4);

        UUID chatSessionId = UUID.randomUUID();
        UUID foodProductId = jdbcTemplate.queryForObject("SELECT id FROM food_product ORDER BY id LIMIT 1", UUID.class);
        jdbcTemplate.update("INSERT INTO chat_session (id, user_id) VALUES (?, ?)", chatSessionId, userId);
        jdbcTemplate.update("""
                INSERT INTO chat_reference (
                    id, session_id, origin, source_type, food_product_id
                )
                VALUES (?, ?, 'USER_SHARED', 'FOOD_PRODUCT', ?)
                """, UUID.randomUUID(), chatSessionId, foodProductId);

        UUID sessionId = createProcessingRecommendationSession(userId, OffsetDateTime.parse("2026-07-28T02:00:00Z"));
        UUID candidateId = createReturnedCandidate(sessionId);
        completeRecommendationSession(sessionId, OffsetDateTime.parse("2026-07-28T02:00:01Z"));
        jdbcTemplate.update("""
                INSERT INTO recommendation_feedback (
                    id, session_id, candidate_id, user_id, event_type, idempotency_key, created_at
                )
                VALUES (?, ?, ?, ?, 'ACCEPTED', 'accepted-1', '2026-07-28T02:05:00Z'::timestamptz)
                """, UUID.randomUUID(), sessionId, candidateId, userId);
        jdbcTemplate.update("""
                INSERT INTO recommendation_feedback (
                    id, session_id, candidate_id, user_id, event_type, rating, idempotency_key, created_at
                )
                VALUES (?, ?, ?, ?, 'LATER_RATED', 4.5, 'rated-1', '2026-07-29T02:05:00Z'::timestamptz)
                """, UUID.randomUUID(), sessionId, candidateId, userId);

        Map<String, Object> exportRow = jdbcTemplate.queryForMap("""
                SELECT explicit_label, later_rating
                FROM public.foodmind_ml_interaction_export_rows_v1(
                    '2026-07-28T00:00:00Z'::timestamptz,
                    '2026-07-29T00:00:00Z'::timestamptz,
                    '2026-07-30T00:00:00Z'::timestamptz
                )
                WHERE candidate_id = ?
                """, candidateId);

        assertThat(exportRow.get("explicit_label")).isEqualTo(1);
        assertThat((BigDecimal) exportRow.get("later_rating")).isEqualByComparingTo("4.5");
    }

    @Test
    void flywaySafetyPropertiesAreConfigured() {
        assertThat(environment.getProperty("spring.flyway.out-of-order")).isEqualTo("false");
        assertThat(environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.flyway.validate-migration-naming")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    private UUID createUser(String suffix) {
        UUID userId = UUID.randomUUID();
        String email = suffix + "-" + userId + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, normalised_email, password_hash, display_name)
                VALUES (?, ?, ?, ?, ?)
                """, userId, email, email.toLowerCase(), "$argon2id$test-placeholder", "Test " + suffix);
        return userId;
    }

    private void insertAuthSession(UUID sessionId, UUID userId, UUID familyId, String tokenHash) {
        jdbcTemplate.update("""
                INSERT INTO auth_session (
                    id, user_id, token_family_id, refresh_token_hash, expires_at, client_type
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', 'WEB')
                """, sessionId, userId, familyId, tokenHash);
    }

    private UUID createProcessingRecommendationSession(UUID userId, OffsetDateTime startedAt) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO recommendation_session (
                    id, user_id, status, request_context, public_contract_version, agent_contract_version,
                    model_status, fallback_status, correlation_id, created_at, started_at
                )
                VALUES (
                    ?, ?, 'PROCESSING', '{}'::jsonb, 'public-v1', 'agent-v1',
                    'PENDING', 'NOT_STARTED', ?, ?, ?
                )
                """, sessionId, userId, UUID.randomUUID(), startedAt, startedAt);
        return sessionId;
    }

    private void completeRecommendationSession(UUID sessionId, OffsetDateTime completedAt) {
        jdbcTemplate.update("""
                UPDATE recommendation_session
                SET status = 'SUCCEEDED',
                    model_version = 'model-v1',
                    model_status = 'SUCCEEDED',
                    fallback_status = 'NOT_REQUIRED',
                    completed_at = ?
                WHERE id = ?
                """, completedAt, sessionId);
    }

    private UUID createReturnedCandidate(UUID sessionId) {
        UUID candidateId = UUID.randomUUID();
        UUID placeMealId = jdbcTemplate.queryForObject("SELECT id FROM place_meal ORDER BY id LIMIT 1", UUID.class);
        jdbcTemplate.update("""
                INSERT INTO recommendation_candidate (
                    id, session_id, place_meal_id, eligibility_status, feature_schema_version,
                    feature_snapshot, candidate_type, rank, evidence_snapshot
                )
                VALUES (?, ?, ?, 'RETURNED', 'features-v1', '{}'::jsonb, 'PERSONAL', 1, '{}'::jsonb)
                """, candidateId, sessionId, placeMealId);
        return candidateId;
    }

    private List<String> queryForList(String sql) {
        return jdbcTemplate.queryForList(sql, String.class);
    }

    private int count(String sql, Object... arguments) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        assertThat(result).isNotNull();
        return result;
    }

    private void assertSqlRejected(String sql, Object... arguments) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, arguments))
                .isInstanceOf(DataAccessException.class);
    }
}
