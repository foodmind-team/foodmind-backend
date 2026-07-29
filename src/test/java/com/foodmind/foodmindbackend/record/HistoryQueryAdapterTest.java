package com.foodmind.foodmindbackend.record;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HistoryQueryAdapterTest extends PostgreSqlContainerSupport {

    private static final UUID FOOD_ID = UUID.fromString("41000000-0000-4000-8000-000000000001");
    private static final UUID DRINK_ID = UUID.fromString("41000000-0000-4000-8000-000000000002");
    private static final UUID DELETED_DRINK_ID = UUID.fromString("41000000-0000-4000-8000-000000000003");
    private static final UUID CUISINE_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE drink_record, food_record, group_invitation, group_membership,
                    trusted_group, auth_session, app_user CASCADE
                """);
    }

    @Test
    void historyBucketsUseExplicitTimezoneAndMondayWeekStart() throws Exception {
        MvcResult owner = register("history-owner@example.test", "History Owner");
        String ownerToken = read(owner, "$.accessToken");
        UUID ownerUserId = UUID.fromString(read(owner, "$.userId"));
        String otherToken = read(register("history-other@example.test", "History Other"), "$.accessToken");
        insertFood(ownerUserId, FOOD_ID, "Late Sunday Supper", "2026-07-26T15:59:00Z", null);
        insertDrink(ownerUserId, DRINK_ID, "Monday Milk Tea", "2026-07-26T16:01:00Z", null);
        insertDrink(ownerUserId, DELETED_DRINK_ID, "Deleted Tea", "2026-07-26T16:02:00Z", OffsetDateTime.parse("2026-07-30T00:00:00Z"));

        mockMvc.perform(get("/api/v1/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .queryParam("from", "2026-07-26")
                        .queryParam("to", "2026-07-28")
                        .queryParam("period", "DAY")
                        .queryParam("types", "FOOD,DRINK")
                        .queryParam("timeZone", "Asia/Singapore")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromUtcInclusive").value("2026-07-25T16:00:00Z"))
                .andExpect(jsonPath("$.toUtcExclusive").value("2026-07-27T16:00:00Z"))
                .andExpect(jsonPath("$.buckets[0].bucketStart").value("2026-07-26"))
                .andExpect(jsonPath("$.buckets[0].foodCount").value(1))
                .andExpect(jsonPath("$.buckets[0].drinkCount").value(0))
                .andExpect(jsonPath("$.buckets[1].bucketStart").value("2026-07-27"))
                .andExpect(jsonPath("$.buckets[1].foodCount").value(0))
                .andExpect(jsonPath("$.buckets[1].drinkCount").value(1))
                .andExpect(jsonPath("$.entries[0].sourceType").value("DRINK"))
                .andExpect(jsonPath("$.entries[0].localBucketStart").value("2026-07-27"))
                .andExpect(jsonPath("$.entries[1].sourceType").value("FOOD"));

        mockMvc.perform(get("/api/v1/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .queryParam("from", "2026-07-26")
                        .queryParam("to", "2026-08-03")
                        .queryParam("period", "WEEK")
                        .queryParam("timeZone", "Asia/Singapore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[0].bucketStart").value("2026-07-20"))
                .andExpect(jsonPath("$.buckets[1].bucketStart").value("2026-07-27"));

        mockMvc.perform(get("/api/v1/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .queryParam("from", "2026-07-26")
                        .queryParam("to", "2026-07-28")
                        .queryParam("period", "DAY")
                        .queryParam("timeZone", "Asia/Singapore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.buckets").isEmpty());
    }

    @Test
    void historySupportsTypeCuisineCursorAndRangeValidation() throws Exception {
        MvcResult owner = register("history-filter@example.test", "History Filter");
        String ownerToken = read(owner, "$.accessToken");
        UUID ownerUserId = UUID.fromString(read(owner, "$.userId"));
        insertFood(ownerUserId, FOOD_ID, "Cuisine Food", "2026-07-28T04:00:00Z", null);
        insertDrink(ownerUserId, DRINK_ID, "Filtered Tea", "2026-07-28T05:00:00Z", null);

        mockMvc.perform(get("/api/v1/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .queryParam("from", "2026-07-28T00:00:00Z")
                        .queryParam("to", "2026-07-29T00:00:00Z")
                        .queryParam("types", "FOOD")
                        .queryParam("cuisineId", CUISINE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].sourceType").value("FOOD"))
                .andExpect(jsonPath("$.entries.length()").value(1));

        MvcResult firstPage = mockMvc.perform(get("/api/v1/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .queryParam("from", "2026-07-28T00:00:00Z")
                        .queryParam("to", "2026-07-29T00:00:00Z")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn();
        String cursor = read(firstPage, "$.nextCursor");

        mockMvc.perform(get("/api/v1/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .queryParam("from", "2026-07-28T00:00:00Z")
                        .queryParam("to", "2026-07-29T00:00:00Z")
                        .queryParam("cursor", cursor)
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].sourceType").value("FOOD"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(get("/api/v1/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2027-02-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("RANGE_MAX"));
    }

    private void insertFood(UUID ownerUserId, UUID id, String name, String occurredAt, OffsetDateTime deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO food_record (
                    id, owner_user_id, meal_name_snapshot, cuisine_id, occurred_at,
                    rating, would_eat_again, visibility, deleted_at
                )
                VALUES (?, ?, ?, ?, ?::timestamptz, 4.0, true, 'PRIVATE', ?)
                """, id, ownerUserId, name, CUISINE_ID, occurredAt, deletedAt);
    }

    private void insertDrink(UUID ownerUserId, UUID id, String name, String occurredAt, OffsetDateTime deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO drink_record (
                    id, owner_user_id, drink_name, shop_name_snapshot, occurred_at,
                    rating, sweetness_level, ice_level, would_buy_again, visibility, deleted_at
                )
                VALUES (?, ?, ?, 'Test Tea Shop', ?::timestamptz, 4.5, 2, 1, true, 'PRIVATE', ?)
                """, id, ownerUserId, name, occurredAt, deletedAt);
    }

    private MvcResult register(String email, String displayName) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "correct horse battery",
                                  "clientType": "WEB",
                                  "deviceLabel": "JUnit"
                                }
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
