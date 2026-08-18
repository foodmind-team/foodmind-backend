package com.foodmind.foodmindbackend.search;

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
class SearchExploreFlowTest extends PostgreSqlContainerSupport {

    private static final String MEAL_ID = "20000000-0000-4000-8000-000000000001";
    private static final String PLACE_ID = "21000000-0000-4000-8000-000000000001";
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
    void searchAndExploreOnlyReturnCurrentlyAuthorisedSources() throws Exception {
        MvcResult owner = register("search-owner@example.test", "Search Owner");
        String ownerToken = read(owner, "$.accessToken");
        String memberToken = read(register("search-member@example.test", "Search Member"), "$.accessToken");
        String secondaryUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM app_user WHERE email = 'search-member@example.test'",
                String.class);

        String privateRecordId = createFoodRecord(ownerToken, "Private saffron keyword", "PRIVATE", null);
        String groupId = createGroup(ownerToken, "Search Group");
        joinGroup(ownerToken, memberToken, groupId);
        String groupRecordId = createFoodRecord(ownerToken, "Searchable group record", "GROUP", groupId);

        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .queryParam("q", "saffron")
                        .queryParam("types", "FOOD_RECORD")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceId", hasItem(privateRecordId)));
        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .queryParam("q", "saffron")
                        .queryParam("types", "FOOD_RECORD")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceId", not(hasItem(privateRecordId))));

        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .queryParam("q", "Searchable group record")
                        .queryParam("types", "FOOD_RECORD")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceId", hasItem(groupRecordId)));
        mockMvc.perform(get("/api/v1/explore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .queryParam("types", "FOOD_RECORD")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceType", hasItem("GROUP_RECORD")))
                .andExpect(jsonPath("$.items[*].sourceId", hasItem(groupRecordId)));

        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", groupId, secondaryUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .queryParam("q", "Searchable group record")
                        .queryParam("types", "FOOD_RECORD")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceId", not(hasItem(groupRecordId))));
        mockMvc.perform(get("/api/v1/explore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .queryParam("types", "FOOD_RECORD")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceId", not(hasItem(groupRecordId))));
    }

    @Test
    void curatedImagesArePublicAndCannotBeCachedStale() throws Exception {
        mockMvc.perform(get("/api/v1/catalogue-images/{sourceId}", PLACE_ID))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CACHE_CONTROL, "no-store"));

        mockMvc.perform(get("/api/v1/catalogue-images/{sourceId}", "00000000-0000-4000-8000-000000000099"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchValidatesBoundsUsesAllowListAndKeepsInputParameterized() throws Exception {
        String accessToken = read(register("search-validation@example.test", "Search Validation"), "$.accessToken");

        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .queryParam("q", "hawker")
                        .queryParam("types", "PLACE,FOOD_PRODUCT")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceType", hasItem("PLACE")));

        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .queryParam("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .queryParam("q", "x".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .queryParam("q", "hawker")
                        .queryParam("types", "MEAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .queryParam("q", "hawker'; drop table app_user; --")
                        .queryParam("types", "PLACE"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());
    }

    private String createFoodRecord(String accessToken, String mealName, String visibility, String groupId) throws Exception {
        return read(mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealId": "%s",
                                  "mealNameSnapshot": "%s",
                                  "placeId": "%s",
                                  "placeNameSnapshot": "Orchard Garden Kitchen",
                                  "cuisineId": "%s",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "comment": "Search permission fixture",
                                  "visibility": "%s",
                                  "groupId": %s
                                }
                                """.formatted(MEAL_ID, mealName, PLACE_ID, CUISINE_ID, visibility, groupId == null ? "null" : "\"" + groupId + "\"")))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
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
