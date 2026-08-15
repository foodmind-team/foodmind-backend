package com.foodmind.foodmindbackend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "foodmind.security.internal-service.token=test-service-token",
        "foodmind.chat.agent.enabled=false"
})
class ChatIdempotencyFlowTest extends PostgreSqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE idempotency_record, chat_message_source, chat_reference, chat_message, chat_session,
                    auth_session, app_user CASCADE
                """);
    }

    @Test
    void retryWithSameKeyReplaysOneStoredAssistantAndChangedPayloadConflicts() throws Exception {
        String token = read(register("chat-idempotency@example.test", "Chat Idempotency"), "$.accessToken");
        String sessionId = createSession(token);
        String key = "chat-message-stable-key";

        MvcResult first = mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Explain tofu protein\",\"useSessionReferences\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedQuestions").isArray())
                .andExpect(jsonPath("$.suggestedDestinations").isArray())
                .andReturn();
        String assistantId = read(first, "$.id");
        List<String> suggestedQuestions = read(first, "$.suggestedQuestions");
        List<String> suggestedDestinations = read(first, "$.suggestedDestinations");

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Explain tofu protein\",\"useSessionReferences\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(assistantId))
                .andExpect(jsonPath("$.suggestedQuestions").value(suggestedQuestions))
                .andExpect(jsonPath("$.suggestedDestinations").value(suggestedDestinations));

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].id").value(assistantId))
                .andExpect(jsonPath("$.items[1].suggestedQuestions").value(suggestedQuestions))
                .andExpect(jsonPath("$.items[1].suggestedDestinations").value(suggestedDestinations));

        UUID parsedSessionId = UUID.fromString(sessionId);
        UUID parsedAssistantId = UUID.fromString(assistantId);
        assertEquals(3, suggestedQuestions.size());
        assertEquals(3, suggestedDestinations.size());
        assertEquals(suggestedQuestions.size(), jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(suggested_questions) FROM chat_message WHERE id = ?",
                Integer.class,
                parsedAssistantId));
        assertEquals(suggestedDestinations.size(), jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(suggested_destinations) FROM chat_message WHERE id = ?",
                Integer.class,
                parsedAssistantId));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM chat_message WHERE session_id = ? AND role = 'USER'",
                        Long.class,
                        parsedSessionId));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM chat_message WHERE session_id = ? AND role = 'ASSISTANT'",
                        Long.class,
                        parsedSessionId));

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Explain tempeh protein\",\"useSessionReferences\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private String createSession(String token) throws Exception {
        return read(mockMvc.perform(post("/api/v1/chat/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Retry\"}"))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
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

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
