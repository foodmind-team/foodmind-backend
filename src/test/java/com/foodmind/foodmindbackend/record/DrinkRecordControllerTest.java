package com.foodmind.foodmindbackend.record;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
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
class DrinkRecordControllerTest extends PostgreSqlContainerSupport {

    private static final String PLACE_ID = "21000000-0000-4000-8000-000000000001";
    private static final String MEDIA_ID = "32000000-0000-4000-8000-000000000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE drink_record, media_asset, group_invitation, group_membership,
                    trusted_group, auth_session, app_user CASCADE
                """);
    }

    @Test
    void ownerCanCreateReadFilterUpdateAndSoftDeletePrivateDrinkRecord() throws Exception {
        MvcResult primary = register("primary-drink@example.test", "Primary Drink");
        String primaryToken = read(primary, "$.accessToken");
        String primaryUserId = read(primary, "$.userId");
        String secondaryToken = read(register("secondary-drink@example.test", "Secondary Drink"), "$.accessToken");
        insertReadyMedia(primaryUserId, MEDIA_ID);

        MvcResult created = mockMvc.perform(post("/api/v1/drink-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "drinkName": "  Iced Matcha Latte  ",
                                  "placeId": "%s",
                                  "shopNameSnapshot": "Orchard Tea Bar",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "price": 5.80,
                                  "currency": "sgd",
                                  "rating": 4.5,
                                  "comment": "Clean tea flavour.",
                                  "sweetnessLevel": 2,
                                  "iceLevel": 1,
                                  "wouldBuyAgain": true,
                                  "visibility": "PRIVATE",
                                  "mediaAssetId": "%s"
                                }
                                """.formatted(PLACE_ID, MEDIA_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.drinkName").value("Iced Matcha Latte"))
                .andExpect(jsonPath("$.price.currency").value("SGD"))
                .andExpect(jsonPath("$.sweetnessLevel").value(2))
                .andReturn();
        String drinkRecordId = read(created, "$.id");

        mockMvc.perform(get("/api/v1/drink-records/{id}", drinkRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Clean tea flavour."))
                .andExpect(jsonPath("$.mediaAssetId").value(MEDIA_ID));

        mockMvc.perform(get("/api/v1/drink-records/{id}", drinkRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/drink-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .queryParam("placeId", PLACE_ID)
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(drinkRecordId))
                .andExpect(jsonPath("$.items[0].comment").value(nullValue()))
                .andExpect(jsonPath("$.items[0].mediaAssetId").value(nullValue()));

        mockMvc.perform(patch("/api/v1/drink-records/{id}", drinkRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5.0,
                                  "sweetnessLevel": 3,
                                  "wouldBuyAgain": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.rating").value(5.0))
                .andExpect(jsonPath("$.wouldBuyAgain").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/drink-records/{id}", drinkRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4.0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(delete("/api/v1/drink-records/{id}", drinkRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/drink-records/{id}", drinkRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidDrinkFieldsAndGroupVisibilityAreRejected() throws Exception {
        MvcResult primary = register("invalid-drink@example.test", "Invalid Drink");
        String accessToken = read(primary, "$.accessToken");
        String userId = read(primary, "$.userId");
        insertPendingMedia(userId, MEDIA_ID);

        mockMvc.perform(post("/api/v1/drink-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "drinkName": "Tea",
                                  "shopNameSnapshot": "Tea Shop",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "sweetnessLevel": 6
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/drink-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "drinkName": "Tea",
                                  "shopNameSnapshot": "Tea Shop",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "visibility": "GROUP",
                                  "groupId": "33000000-0000-4000-8000-000000000001"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/drink-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "drinkName": "Tea",
                                  "shopNameSnapshot": "Tea Shop",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "mediaAssetId": "%s"
                                }
                                """.formatted(MEDIA_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("mediaAssetId"));
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
                VALUES (?, ?, ?, 'image/jpeg', 128, repeat('b', 64), 'READY', CURRENT_TIMESTAMP)
                """, UUID.fromString(mediaId), UUID.fromString(ownerUserId), "drinks/" + mediaId + ".jpg");
    }

    private void insertPendingMedia(String ownerUserId, String mediaId) {
        jdbcTemplate.update("""
                INSERT INTO media_asset (id, owner_user_id, object_key, content_type, byte_size)
                VALUES (?, ?, ?, 'image/jpeg', 128)
                """, UUID.fromString(mediaId), UUID.fromString(ownerUserId), "drinks/" + mediaId + ".jpg");
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
