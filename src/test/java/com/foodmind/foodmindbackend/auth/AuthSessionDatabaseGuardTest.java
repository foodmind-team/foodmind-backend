package com.foodmind.foodmindbackend.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthSessionDatabaseGuardTest extends PostgreSqlContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAuthTables() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_session, app_user CASCADE");
    }

    @Test
    void databaseRejectsTokenIdentityRewritesAndActiveSessionDeletion() {
        UUID userId = createUser();
        UUID sessionId = UUID.randomUUID();
        insertSession(sessionId, userId, UUID.randomUUID(), "a".repeat(64));

        assertSqlRejected("UPDATE auth_session SET refresh_token_hash = ? WHERE id = ?", "b".repeat(64), sessionId);
        assertSqlRejected("DELETE FROM auth_session WHERE id = ?", sessionId);
    }

    @Test
    void databaseRejectsUnrevocationAndRotationRepointing() {
        UUID userId = createUser();
        UUID familyId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();
        insertSession(predecessorId, userId, familyId, "c".repeat(64));
        insertSession(successorId, userId, familyId, "d".repeat(64));

        jdbcTemplate.update("""
                UPDATE auth_session
                SET rotated_at = (SELECT issued_at FROM auth_session WHERE id = ?),
                    replaced_by_session_id = ?
                WHERE id = ?
                """, successorId, successorId, predecessorId);
        jdbcTemplate.update("UPDATE auth_session SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?", successorId);

        assertSqlRejected("UPDATE auth_session SET revoked_at = NULL WHERE id = ?", successorId);
        assertSqlRejected("UPDATE auth_session SET replaced_by_session_id = NULL, rotated_at = NULL WHERE id = ?", predecessorId);
    }

    private UUID createUser() {
        UUID userId = UUID.randomUUID();
        String email = userId + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, normalised_email, password_hash, display_name)
                VALUES (?, ?, ?, ?, ?)
                """, userId, email, email, "$2a$10$placeholderhashplaceholderhashplaceholderhash", "Guard User");
        return userId;
    }

    private void insertSession(UUID sessionId, UUID userId, UUID familyId, String tokenHash) {
        jdbcTemplate.update("""
                INSERT INTO auth_session (
                    id, user_id, token_family_id, refresh_token_hash, expires_at, client_type
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', 'WEB')
                """, sessionId, userId, familyId, tokenHash);
    }

    private void assertSqlRejected(String sql, Object... arguments) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, arguments))
                .isInstanceOf(DataAccessException.class);
    }
}
