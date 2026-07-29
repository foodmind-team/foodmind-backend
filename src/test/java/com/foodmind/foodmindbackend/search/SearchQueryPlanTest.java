package com.foodmind.foodmindbackend.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchQueryPlanTest extends PostgreSqlContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE want_to_try, food_record, auth_session, app_user CASCADE
                """);
    }

    @Test
    void representativeSearchPredicatesUseFtsAndTrigramIndexes() {
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, normalised_email, password_hash, display_name)
                VALUES (?, 'plan-user@example.test', 'plan-user@example.test', 'hash', 'Plan User')
                """, userId);
        jdbcTemplate.update("""
                INSERT INTO food_record (
                    id, owner_user_id, meal_name_snapshot, place_name_snapshot, occurred_at, visibility
                )
                VALUES (?, ?, 'Plan searchable ramen', 'Index Kitchen', '2026-07-28T04:15:00Z', 'PRIVATE')
                """, recordId, userId);
        for (int index = 0; index < 300; index++) {
            jdbcTemplate.update("""
                    INSERT INTO food_record (
                        id, owner_user_id, meal_name_snapshot, place_name_snapshot, occurred_at, visibility
                    )
                    VALUES (?, ?, ?, 'Index Kitchen', '2026-07-28T04:15:00Z', 'PRIVATE')
                    """, UUID.randomUUID(), userId, "Plan filler " + index);
            jdbcTemplate.update("""
                    INSERT INTO place (id, name, place_type, area, address_text, curation_status)
                    VALUES (?, ?, 'CAFE', ?, 'Plan filler address', 'ACTIVE')
                    """, UUID.randomUUID(), "Plan Place " + index, "Plan Area " + index);
        }
        jdbcTemplate.execute("ANALYZE food_record");
        jdbcTemplate.execute("ANALYZE place");

        jdbcTemplate.execute("SET enable_seqscan = off");
        jdbcTemplate.execute("SET enable_indexscan = off");
        jdbcTemplate.execute("SET cpu_tuple_cost = 10");
        String foodRecordPlan;
        String placeTrigramPlan;
        try {
            foodRecordPlan = String.join("\n", jdbcTemplate.queryForList("""
                    EXPLAIN
                    SELECT id
                    FROM food_record
                    WHERE deleted_at IS NULL
                      AND search_vector @@ websearch_to_tsquery('simple', 'ramen')
                    """, String.class));
            placeTrigramPlan = String.join("\n", jdbcTemplate.queryForList("""
                    EXPLAIN
                    SELECT id
                    FROM place
                    WHERE curation_status = 'ACTIVE'
                      AND lower(name) OPERATOR(public.%) 'hawker'
                    """, String.class));
        } finally {
            jdbcTemplate.execute("RESET cpu_tuple_cost");
            jdbcTemplate.execute("RESET enable_indexscan");
            jdbcTemplate.execute("RESET enable_seqscan");
        }

        assertThat(foodRecordPlan).contains("ix_food_record_search_vector_active");
        assertThat(placeTrigramPlan).contains("ix_place_name_trgm_active");
    }
}
