package com.foodmind.foodmindbackend.record;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.record.application.GetMealNotes;
import com.foodmind.foodmindbackend.record.domain.MealNoteView;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
 * @date: 29/07/2026 10:30 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FoodRecordFlowTest extends PostgreSqlContainerSupport {

    private static final String MEAL_ID = "20000000-0000-4000-8000-000000000001";
    private static final String PLACE_ID = "21000000-0000-4000-8000-000000000001";
    private static final String CUISINE_ID = "10000000-0000-4000-8000-000000000001";
    private static final String MEDIA_ID = "32000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GetMealNotes getMealNotes;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE food_record, media_asset, auth_session, app_user CASCADE
                """);
    }

    @Test
    void ownerCanCreateReadFilterUpdateAndSoftDeletePrivateFoodRecord() throws Exception {
        MvcResult primary = register("primary-record@example.test", "Primary Record");
        String primaryToken = read(primary, "$.accessToken");
        String primaryUserId = read(primary, "$.userId");
        String secondaryToken = read(register("secondary-record@example.test", "Secondary Record"), "$.accessToken");
        insertReadyMedia(primaryUserId, MEDIA_ID);

        MvcResult created = mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealId": "%s",
                                  "mealNameSnapshot": "  Hainanese Chicken Rice  ",
                                  "placeId": "%s",
                                  "placeNameSnapshot": "Orchard Garden Kitchen",
                                  "cuisineId": "%s",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "price": 7.50,
                                  "currency": "sgd",
                                  "rating": 4.5,
                                  "comment": "Tender chicken and bright chilli.",
                                  "wouldEatAgain": true,
                                  "visibility": "PRIVATE",
                                  "mediaAssetId": "%s"
                                }
                                """.formatted(MEAL_ID, PLACE_ID, CUISINE_ID, MEDIA_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.mealNameSnapshot").value("Hainanese Chicken Rice"))
                .andExpect(jsonPath("$.price.currency").value("SGD"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String foodRecordId = read(created, "$.id");

        mockMvc.perform(get("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.comment").value("Tender chicken and bright chilli."))
                .andExpect(jsonPath("$.mediaAssetId").value(MEDIA_ID))
                .andExpect(jsonPath("$.cuisineCode").value("SINGAPOREAN"));

        mockMvc.perform(get("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .queryParam("cuisineId", CUISINE_ID)
                        .queryParam("sort", "occurredAt,desc")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(foodRecordId))
                .andExpect(jsonPath("$.items[0].comment").value(nullValue()))
                .andExpect(jsonPath("$.items[0].mediaAssetId").value(nullValue()));

        mockMvc.perform(patch("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5.0,
                                  "comment": "Even better on the second visit.",
                                  "wouldEatAgain": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.rating").value(5.0))
                .andExpect(jsonPath("$.wouldEatAgain").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4.0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(patch("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken))
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4.0}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/food-records/{id}", foodRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidFieldsVisibilityCombinationAndMediaAreRejectedBeforeInsert() throws Exception {
        MvcResult registered = register("invalid-record@example.test", "Invalid Record");
        String accessToken = read(registered, "$.accessToken");
        String userId = read(registered, "$.userId");
        insertPendingMedia(userId, MEDIA_ID);

        mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealNameSnapshot": "Soup",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "price": 3.00,
                                  "rating": 7.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealNameSnapshot": "Soup",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "visibility": "GROUP",
                                  "groupId": "33000000-0000-4000-8000-000000000001"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealNameSnapshot": "Soup",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "mediaAssetId": "%s"
                                }
                                """.formatted(MEDIA_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("mediaAssetId"));
    }

    @Test
    void mealNoteProjectionContainsOnlySafeAuthorisedFields() throws Exception {
        MvcResult registered = register("meal-note@example.test", "Meal Note");
        String accessToken = read(registered, "$.accessToken");
        String userId = read(registered, "$.userId");

        MvcResult created = mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealId": "%s",
                                  "mealNameSnapshot": "Hainanese Chicken Rice",
                                  "placeId": "%s",
                                  "placeNameSnapshot": "Orchard Garden Kitchen",
                                  "cuisineId": "%s",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "rating": 4.5,
                                  "comment": "Private note text",
                                  "wouldEatAgain": true
                                }
                                """.formatted(MEAL_ID, PLACE_ID, CUISINE_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        String foodRecordId = read(created, "$.id");

        List<MealNoteView> notes = getMealNotes.forUser(UUID.fromString(userId), 10);

        org.assertj.core.api.Assertions.assertThat(notes)
                .extracting(MealNoteView::id)
                .contains(UUID.fromString(foodRecordId));
        org.assertj.core.api.Assertions.assertThat(notes.get(0).mealName()).isEqualTo("Hainanese Chicken Rice");
        org.assertj.core.api.Assertions.assertThat(MealNoteView.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("ownerUserId", "comment", "mediaAssetId");
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

    private void insertReadyMedia(String ownerUserId, String mediaId) {
        jdbcTemplate.update("""
                INSERT INTO media_asset (
                    id, owner_user_id, object_key, content_type, byte_size, checksum_sha256,
                    status, finalised_at
                )
                VALUES (?, ?, ?, 'image/jpeg', 128, repeat('a', 64), 'READY', CURRENT_TIMESTAMP)
                """, UUID.fromString(mediaId), UUID.fromString(ownerUserId), "records/" + mediaId + ".jpg");
    }

    private void insertPendingMedia(String ownerUserId, String mediaId) {
        jdbcTemplate.update("""
                INSERT INTO media_asset (id, owner_user_id, object_key, content_type, byte_size)
                VALUES (?, ?, ?, 'image/jpeg', 128)
                """, UUID.fromString(mediaId), UUID.fromString(ownerUserId), "records/" + mediaId + ".jpg");
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
