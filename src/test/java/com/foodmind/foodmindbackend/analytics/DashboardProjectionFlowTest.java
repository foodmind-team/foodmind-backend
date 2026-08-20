package com.foodmind.foodmindbackend.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description: Verifies V10 dashboard projections preserve owner isolation, deletion, and currencies.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 05:00 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
class DashboardProjectionFlowTest extends PostgreSqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void keepsCurrenciesSeparateAndExcludesDeletedAndOtherOwnerRecords() throws Exception {
        String ownerToken = token(register("analytics-owner@example.test", "Analytics Owner"));
        String otherToken = token(register("analytics-other@example.test", "Analytics Other"));
        UUID ownerId = userId("analytics-owner@example.test");
        UUID otherId = userId("analytics-other@example.test");
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");

        insertFood(ownerId, occurredAt, "12.50", "SGD", true, false);
        insertDrink(ownerId, occurredAt, "8.00", "USD", false, false);
        insertFood(ownerId, occurredAt, "99.00", "SGD", true, true);
        insertFood(otherId, occurredAt, "77.00", "SGD", true, false);

        mockMvc.perform(get("/api/v1/dashboard")
                        .queryParam("from", "2026-07-27")
                        .queryParam("to", "2026-08-03")
                        .queryParam("groupBy", "WEEK")
                        .queryParam("timeZone", "Asia/Singapore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.spendingTotals.length()").value(2))
                .andExpect(jsonPath("$.spendingTotals[?(@.currency == 'SGD')].value").value(12.50))
                .andExpect(jsonPath("$.spendingTotals[?(@.currency == 'USD')].value").value(8.00))
                .andExpect(jsonPath("$.metrics[?(@.code == 'FOOD_DRINK_COUNT')].value").value(2));

        mockMvc.perform(get("/api/v1/dashboard")
                        .queryParam("from", "2026-07-27")
                        .queryParam("to", "2026-08-03")
                        .queryParam("groupBy", "WEEK")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics[?(@.code == 'FOOD_DRINK_COUNT')].value").value(1));
    }

    @Test
    void weeklyRecapIncludesOwnerOnlyClassifiedCuisineMix() throws Exception {
        String ownerToken = token(register("recap-cuisine-owner@example.test", "Recap Cuisine Owner"));
        register("recap-cuisine-other@example.test", "Recap Cuisine Other");
        UUID ownerId = userId("recap-cuisine-owner@example.test");
        UUID otherId = userId("recap-cuisine-other@example.test");
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");

        insertCuisineFood(ownerId, occurredAt, "INDIAN", false);
        insertCuisineFood(ownerId, occurredAt, "INDIAN", false);
        insertCuisineFood(ownerId, occurredAt, "CHINESE", false);
        insertCuisineFood(ownerId, occurredAt, null, false);
        insertCuisineFood(ownerId, occurredAt, "JAPANESE", true);
        insertCuisineFood(otherId, occurredAt, "MALAY", false);

        mockMvc.perform(get("/api/v1/weekly-recaps/2026-07-27")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics[?(@.code == 'CUISINE_DISTRIBUTION')].length()").value(2))
                .andExpect(jsonPath("$.metrics[?(@.dimension == 'INDIAN')].dimensionLabel").value("Indian"))
                .andExpect(jsonPath("$.metrics[?(@.dimension == 'INDIAN')].value").value(2))
                .andExpect(jsonPath("$.metrics[?(@.dimension == 'CHINESE')].dimensionLabel").value("Chinese"))
                .andExpect(jsonPath("$.metrics[?(@.dimension == 'CHINESE')].value").value(1));
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

    private void insertFood(UUID ownerId, OffsetDateTime occurredAt, String price, String currency, boolean wouldAgain, boolean deleted) {
        jdbc.update("""
                        INSERT INTO food_record (
                            id, owner_user_id, meal_name_snapshot, occurred_at, price, currency,
                            rating, would_eat_again, visibility, deleted_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PRIVATE', ?)
                        """, UUID.randomUUID(), ownerId, "Fixture meal", occurredAt, new BigDecimal(price), currency,
                new BigDecimal("4.0"), wouldAgain, deleted ? OffsetDateTime.now().plusMinutes(1) : null);
    }

    private void insertDrink(UUID ownerId, OffsetDateTime occurredAt, String price, String currency, boolean wouldAgain, boolean deleted) {
        jdbc.update("""
                        INSERT INTO drink_record (
                            id, owner_user_id, drink_name, shop_name_snapshot, occurred_at, price,
                            currency, rating, would_buy_again, visibility, deleted_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PRIVATE', ?)
                        """, UUID.randomUUID(), ownerId, "Fixture drink", "Fixture shop", occurredAt, new BigDecimal(price),
                currency, new BigDecimal("3.0"), wouldAgain, deleted ? OffsetDateTime.now().plusMinutes(1) : null);
    }

    private void insertCuisineFood(UUID ownerId, OffsetDateTime occurredAt, String cuisineCode, boolean deleted) {
        UUID cuisineId = cuisineCode == null ? null : jdbc.queryForObject(
                "SELECT id FROM cuisine WHERE code = ?",
                UUID.class,
                cuisineCode);
        jdbc.update("""
                        INSERT INTO food_record (
                            id, owner_user_id, meal_name_snapshot, cuisine_id, occurred_at,
                            visibility, deleted_at)
                        VALUES (?, ?, ?, ?, ?, 'PRIVATE', ?)
                        """, UUID.randomUUID(), ownerId, "Cuisine fixture " + cuisineCode, cuisineId, occurredAt,
                deleted ? OffsetDateTime.now().plusMinutes(1) : null);
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
    }

    private String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
