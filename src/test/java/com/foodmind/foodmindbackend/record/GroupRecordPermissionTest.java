package com.foodmind.foodmindbackend.record;

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
class GroupRecordPermissionTest extends PostgreSqlContainerSupport {

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
    void groupVisibilityRequiresCurrentMembershipForCreateAndGroupSwitches() throws Exception {
        MvcResult owner = register("matrix-owner@example.test", "Matrix Owner");
        String ownerToken = read(owner, "$.accessToken");
        String memberToken = read(register("matrix-member@example.test", "Matrix Member"), "$.accessToken");
        String otherToken = read(register("matrix-other@example.test", "Matrix Other"), "$.accessToken");

        String firstGroup = createGroup(ownerToken, "First Group");
        String secondGroup = createGroup(ownerToken, "Second Group");
        join(memberToken, firstGroup, ownerToken);

        mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(firstGroup, "GROUP")))
                .andExpect(status().isNotFound());

        String recordId = read(mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(firstGroup, "GROUP")))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(get("/api/v1/food-records/{id}", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/food-records/{id}", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visibility": "GROUP",
                                  "groupId": "%s"
                                }
                                """.formatted(secondGroup)))
                .andExpect(status().isNotFound());

        String memberUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM app_user WHERE email = 'matrix-member@example.test'",
                String.class);
        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", firstGroup, memberUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/food-records/{id}", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/food-records/{id}", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/food-records/{id}", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Owner record retained after leave\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Owner record retained after leave"));

        mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(firstGroup, "GROUP")))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/groups/{groupId}", firstGroup)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(firstGroup, "GROUP")))
                .andExpect(status().isNotFound());
    }

    private String createGroup(String accessToken, String name) throws Exception {
        return read(mockMvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void join(String memberToken, String groupId, String ownerToken) throws Exception {
        String token = read(mockMvc.perform(post("/api/v1/groups/{groupId}/invitations", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":5}"))
                .andExpect(status().isCreated())
                .andReturn(), "$.token");
        mockMvc.perform(post("/api/v1/group-invitations/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isOk());
    }

    private String recordJson(String groupId, String visibility) {
        return """
                {
                  "mealNameSnapshot": "Group noodles",
                  "occurredAt": "2026-07-28T04:15:00Z",
                  "visibility": "%s",
                  "groupId": "%s"
                }
                """.formatted(visibility, groupId);
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
