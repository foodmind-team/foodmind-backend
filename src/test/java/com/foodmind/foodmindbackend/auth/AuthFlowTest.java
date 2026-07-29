package com.foodmind.foodmindbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.auth.domain.RefreshToken;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthFlowTest extends PostgreSqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanAuthTables() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_session, app_user CASCADE");
    }

    @Test
    void registrationHashesPasswordNormalisesEmailAndAuthenticatesCurrentUser() throws Exception {
        MvcResult result = register(" Primary.User@Example.TEST ", "Primary User", "correct horse battery");
        String accessToken = read(result, "$.accessToken");

        Map<String, Object> userRow = jdbcTemplate.queryForMap("""
                SELECT email, normalised_email, password_hash
                FROM app_user
                WHERE normalised_email = 'primary.user@example.test'
                """);
        assertThat(userRow.get("email")).isEqualTo("Primary.User@Example.TEST");
        assertThat(userRow.get("normalised_email")).isEqualTo("primary.user@example.test");
        assertThat(userRow.get("password_hash")).isNotEqualTo("correct horse battery");
        assertThat(passwordEncoder.matches("correct horse battery", (String) userRow.get("password_hash"))).isTrue();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("Primary.User@Example.TEST"))
                .andExpect(jsonPath("$.displayName").value("Primary User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Singapore"));
    }

    @Test
    void duplicateNormalisedEmailIsAConflict() throws Exception {
        register("duplicate@example.test", "Duplicate User", "correct horse battery");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " DUPLICATE@example.test ",
                                  "displayName": "Another User",
                                  "password": "correct horse battery",
                                  "clientType": "WEB"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void loginRejectsWrongUnknownAndSuspendedAccounts() throws Exception {
        register("login@example.test", "Login User", "correct horse battery");

        login("login@example.test", "wrong password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        login("unknown@example.test", "correct horse battery")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        UUID suspendedUserId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (
                    id, email, normalised_email, password_hash, display_name, status
                )
                VALUES (?, ?, ?, ?, ?, 'SUSPENDED')
                """,
                suspendedUserId,
                "suspended@example.test",
                "suspended@example.test",
                passwordEncoder.encode("correct horse battery"),
                "Suspended User");

        login("suspended@example.test", "correct horse battery")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void refreshRotatesTokensAndReuseRevokesTheFamily() throws Exception {
        MvcResult registered = register("refresh@example.test", "Refresh User", "correct horse battery");
        String firstRefresh = read(registered, "$.refreshToken");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(firstRefresh)))
                .andExpect(status().isOk())
                .andReturn();
        String secondRefresh = read(refreshed, "$.refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        Map<String, Object> rotated = jdbcTemplate.queryForMap("""
                SELECT token_family_id, rotated_at, replaced_by_session_id
                FROM auth_session
                WHERE refresh_token_hash = ?
                """, RefreshToken.fromRaw(firstRefresh).hash());
        assertThat(rotated.get("rotated_at")).isNotNull();
        assertThat(rotated.get("replaced_by_session_id")).isNotNull();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(firstRefresh)))
                .andExpect(status().isUnauthorized());

        UUID familyId = (UUID) rotated.get("token_family_id");
        List<Map<String, Object>> familyRows = jdbcTemplate.queryForList(
                "SELECT revoked_at FROM auth_session WHERE token_family_id = ?", familyId);
        assertThat(familyRows).allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(secondRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAndLogoutAllRevokeRefreshSessions() throws Exception {
        MvcResult registered = register("logout@example.test", "Logout User", "correct horse battery");
        String accessToken = read(registered, "$.accessToken");
        String refreshToken = read(registered, "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isUnauthorized());

        MvcResult firstLogin = login("logout@example.test", "correct horse battery")
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondLogin = login("logout@example.test", "correct horse battery")
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(read(firstLogin, "$.accessToken"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(read(firstLogin, "$.refreshToken"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(read(secondLogin, "$.refreshToken"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityDistinguishesMissingAuthenticationFromDeniedAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        MvcResult registered = register("matrix@example.test", "Matrix User", "correct horse battery");
        String accessToken = read(registered, "$.accessToken");
        mockMvc.perform(get("/internal/v1/admin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void accessTokenValidationFailsAfterAccountSuspension() throws Exception {
        MvcResult registered = register("status@example.test", "Status User", "correct horse battery");
        String accessToken = read(registered, "$.accessToken");
        jdbcTemplate.update("UPDATE app_user SET status = 'SUSPENDED' WHERE normalised_email = 'status@example.test'");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private MvcResult register(String email, String displayName, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "%s",
                                  "clientType": "WEB",
                                  "deviceLabel": "JUnit"
                                }
                                """.formatted(email, displayName, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "clientType": "WEB",
                          "deviceLabel": "JUnit"
                        }
                        """.formatted(email, password)));
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String refreshJson(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }
}
