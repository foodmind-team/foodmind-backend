package com.foodmind.foodmindbackend.wanttotry;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
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
 * @date: 30/07/2026 02:01 am
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WantToTryFlowTest extends PostgreSqlContainerSupport {

    private static final String MEAL_ID = "20000000-0000-4000-8000-000000000001";
    private static final String PLACE_ID = "21000000-0000-4000-8000-000000000001";
    private static final String PRODUCT_ID = "23000000-0000-4000-8000-000000000001";
    private static final String CUISINE_ID = "10000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE want_to_try, group_recommendation_share, recommendation_candidate, recommendation_session,
                    food_record, group_invitation, group_membership, trusted_group, auth_session, app_user CASCADE
                """);
    }

    @Test
    void ownerCanSaveListDeduplicateAndDeleteSupportedSources() throws Exception {
        String primaryToken = read(register("saved-primary@example.test", "Saved Primary"), "$.accessToken");
        String secondaryToken = read(register("saved-secondary@example.test", "Saved Secondary"), "$.accessToken");
        String foodRecordId = createPrivateFoodRecord(primaryToken, "Saved private chicken rice");

        String firstSaveId = read(save(primaryToken, "FOOD_RECORD", foodRecordId, "  remember this  ")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.note").value("remember this"))
                        .andExpect(jsonPath("$.sourceAvailable").value(true))
                        .andReturn(),
                "$.id");
        String duplicateSaveId = read(save(primaryToken, "FOOD_RECORD", foodRecordId, "duplicate note")
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.id");

        org.assertj.core.api.Assertions.assertThat(duplicateSaveId).isEqualTo(firstSaveId);
        save(primaryToken, "MEAL", MEAL_ID, "Meal").andExpect(status().isCreated());
        save(primaryToken, "FOOD_PRODUCT", PRODUCT_ID, "Product").andExpect(status().isCreated());
        save(primaryToken, "PLACE", PLACE_ID, "Place").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/want-to-try")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[*].sourceType", hasItem("FOOD_RECORD")))
                .andExpect(jsonPath("$.items[*].sourceType", hasItem("MEAL")))
                .andExpect(jsonPath("$.items[*].sourceType", hasItem("FOOD_PRODUCT")))
                .andExpect(jsonPath("$.items[*].sourceType", hasItem("PLACE")));

        save(secondaryToken, "FOOD_RECORD", foodRecordId, "must not persist")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/want-to-try/{id}", firstSaveId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/want-to-try/{id}", firstSaveId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/want-to-try")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(firstSaveId))));
    }

    @Test
    void listedGroupSourceBecomesExplicitlyUnavailableAfterRevocation() throws Exception {
        MvcResult owner = register("saved-owner@example.test", "Saved Owner");
        String ownerToken = read(owner, "$.accessToken");
        String secondaryToken = read(register("saved-member@example.test", "Saved Member"), "$.accessToken");
        String secondaryUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM app_user WHERE email = 'saved-member@example.test'",
                String.class);

        String groupId = createGroup(ownerToken, "Saved Group");
        joinGroup(ownerToken, secondaryToken, groupId);
        String groupRecordId = createGroupFoodRecord(ownerToken, groupId, "Revocable saved group noodles");

        String saveId = read(save(secondaryToken, "FOOD_RECORD", groupRecordId, "member save")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.sourceAvailable").value(true))
                        .andReturn(),
                "$.id");
        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", groupId, secondaryUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/want-to-try")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '%s')].sourceAvailable".formatted(saveId)).value(hasItem(false)))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].source".formatted(saveId)).value(hasItem((Object) null)));
    }

    private org.springframework.test.web.servlet.ResultActions save(
            String accessToken,
            String sourceType,
            String sourceId,
            String note) throws Exception {
        return mockMvc.perform(post("/api/v1/want-to-try")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "sourceType": "%s",
                          "sourceId": "%s",
                          "note": "%s"
                        }
                        """.formatted(sourceType, sourceId, note)));
    }

    private String createPrivateFoodRecord(String accessToken, String mealName) throws Exception {
        return read(mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(mealName, "PRIVATE", null)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private String createGroupFoodRecord(String accessToken, String groupId, String mealName) throws Exception {
        return read(mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(mealName, "GROUP", groupId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private String recordJson(String mealName, String visibility, String groupId) {
        return """
                {
                  "mealId": "%s",
                  "mealNameSnapshot": "%s",
                  "placeId": "%s",
                  "placeNameSnapshot": "Orchard Garden Kitchen",
                  "cuisineId": "%s",
                  "occurredAt": "2026-07-28T04:15:00Z",
                  "comment": "Want to Try source fixture",
                  "visibility": "%s",
                  "groupId": %s
                }
                """.formatted(MEAL_ID, mealName, PLACE_ID, CUISINE_ID, visibility, groupId == null ? "null" : "\"" + groupId + "\"");
    }

    private String createGroup(String accessToken, String name) throws Exception {
        return read(mockMvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void joinGroup(String ownerToken, String memberToken, String groupId) throws Exception {
        String invitationToken = read(mockMvc.perform(post("/api/v1/groups/{groupId}/invitations", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":5}"))
                .andExpect(status().isCreated())
                .andReturn(), "$.token");
        mockMvc.perform(post("/api/v1/group-invitations/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(invitationToken)))
                .andExpect(status().isOk());
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
