package com.foodmind.foodmindbackend.chat;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "foodmind.security.internal-service.token=test-service-token",
        "foodmind.chat.agent.enabled=false"
})
class ChatFlowTest extends PostgreSqlContainerSupport {

    private static final String MEAL_ID = "20000000-0000-4000-8000-000000000001";
    private static final String PLACE_ID = "21000000-0000-4000-8000-000000000001";
    private static final String PRODUCT_ID = "23000000-0000-4000-8000-000000000001";
    private static final String CUISINE_ID = "10000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DelegationTokenIssuer delegationTokenIssuer;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE chat_message_source, chat_reference, chat_message, chat_session,
                    want_to_try, group_recommendation_share, recommendation_candidate, recommendation_session,
                    food_record, group_invitation, group_membership, trusted_group, auth_session, app_user CASCADE
                """);
    }

    @Test
    void sessionsMessagesAndReferencesAreOwnerScoped() throws Exception {
        String ownerToken = read(register("chat-owner@example.test", "Chat Owner"), "$.accessToken");
        String otherToken = read(register("chat-other@example.test", "Chat Other"), "$.accessToken");

        String sessionId = createSession(ownerToken, "Lunch notes");
        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/references", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType": "FOOD_PRODUCT",
                                  "sourceId": "%s"
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("USER_SHARED"))
                .andExpect(jsonPath("$.introducedByMessageId").doesNotExist());

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"summarise this product\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.route").value("SUMMARY"))
                .andExpect(jsonPath("$.responseStatus").value("FALLBACK_SUCCEEDED"))
                .andExpect(jsonPath("$.sources[*].sourceId", hasItem(PRODUCT_ID)));

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].role", hasItem("USER")))
                .andExpect(jsonPath("$.items[*].role", hasItem("ASSISTANT")));

        mockMvc.perform(delete("/api/v1/chat/sessions/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/chat/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", not(hasItem(sessionId))));
    }

    @Test
    void privateCrossUserSourceCannotBeShared() throws Exception {
        String ownerToken = read(register("chat-record-owner@example.test", "Record Owner"), "$.accessToken");
        String otherToken = read(register("chat-record-other@example.test", "Record Other"), "$.accessToken");
        String privateRecordId = createFoodRecord(ownerToken, "Secret laksa", "PRIVATE", null);
        String otherSessionId = createSession(otherToken, "Other session");

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/references", otherSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType": "FOOD_RECORD",
                                  "sourceId": "%s"
                                }
                                """.formatted(privateRecordId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalToolsRequireServiceIdentityDelegationAndLiveReferenceAuthorization() throws Exception {
        String ownerToken = read(register("chat-tool-owner@example.test", "Tool Owner"), "$.accessToken");
        String memberToken = read(register("chat-tool-member@example.test", "Tool Member"), "$.accessToken");
        String memberUserId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM app_user WHERE email = 'chat-tool-member@example.test'",
                String.class);
        String groupId = createGroup(ownerToken, "Chat Tool Group");
        joinGroup(ownerToken, memberToken, groupId);
        String groupRecordId = createFoodRecord(ownerToken, "Delegated prata", "GROUP", groupId);
        String memberSessionId = createSession(memberToken, "Shared group source");
        String referenceId = read(mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/references", memberSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType": "FOOD_RECORD",
                                  "sourceId": "%s"
                                }
                                """.formatted(groupRecordId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        UUID actorId = UUID.fromString(memberUserId);
        String searchDelegation = delegationTokenIssuer.issue(
                        actorId,
                        "chat-test-trace",
                        List.of(DelegationTokenIssuer.SCOPE_CHAT_SEARCH),
                        List.of(UUID.fromString(referenceId)))
                .token();
        mockMvc.perform(post("/internal/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FoodMind-Delegation", bearer(searchDelegation))
                        .content("{\"query\":\"Delegated prata\",\"sourceTypes\":[\"FOOD_RECORD\"],\"size\":10}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-service-token"))
                        .header("X-FoodMind-Delegation", bearer("forged"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Delegated prata\",\"sourceTypes\":[\"FOOD_RECORD\"],\"size\":10}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-service-token"))
                        .header("X-FoodMind-Delegation", bearer(searchDelegation))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Delegated prata\",\"sourceTypes\":[\"FOOD_RECORD\"],\"size\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sourceId", hasItem(groupRecordId)));

        String resolveDelegation = delegationTokenIssuer.issue(
                        actorId,
                        "chat-test-trace",
                        List.of(DelegationTokenIssuer.SCOPE_CHAT_REFERENCE_RESOLVE),
                        List.of(UUID.fromString(referenceId)))
                .token();
        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", groupId, memberUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/internal/v1/references/resolve")
                        .header(HttpHeaders.AUTHORIZATION, bearer("test-service-token"))
                        .header("X-FoodMind-Delegation", bearer(resolveDelegation))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "referenceIds": ["%s"]
                                }
                                """.formatted(memberSessionId, referenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(false));
        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", memberSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Summarise the group food record I shared.",
                                  "referenceIds": ["%s"]
                                }
                                """.formatted(referenceId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedRecommendationAndCookingIntentDoesNotInvokeThoseWorkflows() throws Exception {
        String accessToken = read(register("chat-unsupported@example.test", "Chat Unsupported"), "$.accessToken");
        String sessionId = createSession(accessToken, "Unsupported");

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"recommend what I should cook tonight\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.route").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.responseStatus").value("UNSUPPORTED"));
        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Recommend a new dinner and cook it for me.",
                                  "route": "RECOMMENDATION"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ordinaryChatWithoutSharedSourcesFallsBackToSupportedNavigation() throws Exception {
        String accessToken = read(register("chat-navigation@example.test", "Chat Navigation"), "$.accessToken");
        String sessionId = createSession(accessToken, "Navigation");

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Where can I find my saved food records?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.route").value("NAVIGATION"))
                .andExpect(jsonPath("$.responseStatus").value("FALLBACK_SUCCEEDED"))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    private String createSession(String accessToken, String title) throws Exception {
        return read(mockMvc.perform(post("/api/v1/chat/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
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
                                  "comment": "Chat permission fixture",
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
