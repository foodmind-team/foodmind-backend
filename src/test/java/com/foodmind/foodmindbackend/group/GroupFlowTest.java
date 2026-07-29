package com.foodmind.foodmindbackend.group;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * @date: 29/07/2026 11:55 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupFlowTest extends PostgreSqlContainerSupport {

    private static final String MEAL_ID = "20000000-0000-4000-8000-000000000001";
    private static final String PLACE_MEAL_ID = "22000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE group_recommendation_share, recommendation_candidate, recommendation_session,
                    food_record, group_invitation, group_membership, trusted_group, auth_session, app_user CASCADE
                """);
    }

    @Test
    void ownerCreatesInvitesFeedsRemovesAndArchivesGroup() throws Exception {
        MvcResult primary = register("group-primary@example.test", "Group Primary");
        String primaryToken = read(primary, "$.accessToken");
        String primaryUserId = read(primary, "$.userId");
        String secondaryToken = read(register("group-secondary@example.test", "Group Secondary"), "$.accessToken");
        String unrelatedToken = read(register("group-unrelated@example.test", "Group Unrelated"), "$.accessToken");

        MvcResult createdGroup = mockMvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Lunch Crew  ",
                                  "description": "People I trust for food notes."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lunch Crew"))
                .andExpect(jsonPath("$.createdByUserId").value(primaryUserId))
                .andReturn();
        String groupId = read(createdGroup, "$.id");

        mockMvc.perform(get("/api/v1/groups/{groupId}/members", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].displayName").value("Group Primary"));

        mockMvc.perform(post("/api/v1/groups/{groupId}/invitations", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        MvcResult invitation = mockMvc.perform(post("/api/v1/groups/{groupId}/invitations", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInHours\":24,\"maxUses\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", startsWith("")))
                .andReturn();
        String token = read(invitation, "$.token");
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM group_invitation WHERE group_id = ?",
                String.class,
                UUID.fromString(groupId));
        org.assertj.core.api.Assertions.assertThat(storedHash)
                .hasSize(64)
                .doesNotContain(token);

        mockMvc.perform(post("/api/v1/group-invitations/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mockMvc.perform(post("/api/v1/group-invitations/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(unrelatedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isNotFound());

        MvcResult groupRecord = createFoodRecord(primaryToken, groupId, "GROUP", "Shared laksa note");
        String groupRecordId = read(groupRecord, "$.id");
        createFoodRecord(primaryToken, null, "PRIVATE", "Private mee pok note");

        mockMvc.perform(get("/api/v1/food-records/{id}", groupRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Shared laksa note"));
        mockMvc.perform(get("/api/v1/food-records/{id}", groupRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unrelatedToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/groups/{groupId}/feed", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sourceType").value("FOOD_RECORD"))
                .andExpect(jsonPath("$.items[0].mealNameSnapshot").value("Laksa"));

        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", groupId, primaryUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", groupId, read(register("outsider@example.test", "Outsider"), "$.userId"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", groupId, read(register("group-secondary-copy@example.test", "Copy"), "$.userId"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNoContent());

        String secondaryUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM app_user WHERE email = 'group-secondary@example.test'",
                String.class);
        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", groupId, secondaryUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/food-records/{id}", groupRecordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/groups/{groupId}/feed", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/groups/{groupId}", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/v1/groups/{groupId}/feed", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void recommendationShareRequiresCandidateOwnershipAndMembership() throws Exception {
        MvcResult primary = register("share-primary@example.test", "Share Primary");
        String primaryToken = read(primary, "$.accessToken");
        String primaryUserId = read(primary, "$.userId");
        String secondaryToken = read(register("share-secondary@example.test", "Share Secondary"), "$.accessToken");

        String groupId = read(mockMvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Recommendation Crew\"}"))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        String token = read(mockMvc.perform(post("/api/v1/groups/{groupId}/invitations", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":2}"))
                .andExpect(status().isCreated())
                .andReturn(), "$.token");
        mockMvc.perform(post("/api/v1/group-invitations/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isOk());

        UUID sessionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO recommendation_session (
                    id, user_id, request_context, public_contract_version, correlation_id
                )
                VALUES (?, ?, '{}'::jsonb, 'test-v1', ?)
                """, sessionId, UUID.fromString(primaryUserId), UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO recommendation_candidate (
                    id, session_id, place_meal_id, eligibility_status, evidence_snapshot
                )
                VALUES (?, ?, ?, 'ELIGIBLE', '{}'::jsonb)
                """, candidateId, sessionId, UUID.fromString(PLACE_MEAL_ID));

        mockMvc.perform(post("/api/v1/groups/{groupId}/recommendation-shares", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recommendationCandidateId": "%s",
                                  "message": "Looks good"
                                }
                                """.formatted(candidateId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/groups/{groupId}/recommendation-shares", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recommendationCandidateId": "%s",
                                  "message": "Try this together"
                                }
                                """.formatted(candidateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommendationCandidateId").value(candidateId.toString()));

        mockMvc.perform(get("/api/v1/groups/{groupId}/feed", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourceType").value("RECOMMENDATION_SHARE"))
                .andExpect(jsonPath("$.items[0].message").value("Try this together"));
    }

    private MvcResult createFoodRecord(String accessToken, String groupId, String visibility, String comment) throws Exception {
        String groupField = groupId == null ? "" : ",\"groupId\":\"" + groupId + "\"";
        return mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealId": "%s",
                                  "mealNameSnapshot": "Laksa",
                                  "occurredAt": "2026-07-28T04:15:00Z",
                                  "comment": "%s",
                                  "visibility": "%s"
                                  %s
                                }
                                """.formatted(MEAL_ID, comment, visibility, groupField)))
                .andExpect(status().isCreated())
                .andReturn();
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
