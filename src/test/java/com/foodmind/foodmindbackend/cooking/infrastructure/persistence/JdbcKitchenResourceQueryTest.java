package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentKitchenResourceSnapshot;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootTest
class JdbcKitchenResourceQueryTest extends PostgreSqlContainerSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcKitchenResourceQuery query;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("TRUNCATE TABLE kitchen_resource, app_user CASCADE",
                new MapSqlParameterSource());
        query = new JdbcKitchenResourceQuery(jdbcTemplate);
    }

    @Test
    void returnsOnlyOwnedResourcesWithCapabilities() {
        UUID owner = insertUser("owner@example.test");
        UUID other = insertUser("other@example.test");

        insertResource(owner, "stove", "4", "burners", List.of("induction"), true);
        insertResource(owner, "oven", null, null, List.of("convection"), false);
        insertResource(other, "stove", "2", "burners", List.of(), true);

        List<AgentKitchenResourceSnapshot> resources = query.resources(owner);

        assertThat(resources).hasSize(2);
        assertThat(resources).extracting(AgentKitchenResourceSnapshot::resourceType)
                .containsExactly("oven", "stove");
        AgentKitchenResourceSnapshot stove = resources.get(1);
        assertThat(stove.capacity()).isEqualByComparingTo("4");
        assertThat(stove.capabilities()).containsExactly("induction");
        assertThat(stove.available()).isTrue();
        assertThat(resources.get(0).available()).isFalse();
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, normalised_email, password_hash, display_name)
                VALUES (:id, :email, :email, 'hash', 'Test User')
                """,
                new MapSqlParameterSource("id", id).addValue("email", email));
        return id;
    }

    private void insertResource(UUID userId, String type, String capacity, String capacityUnit,
                                List<String> capabilities, boolean available) {
        jdbcTemplate.update("""
                INSERT INTO kitchen_resource
                    (id, user_id, resource_type, name, capacity, capacity_unit, capabilities, available)
                VALUES (:id, :userId, :type, :name, :capacity, :capacityUnit, CAST(:capabilities AS text[]), :available)
                """,
                new MapSqlParameterSource("id", UUID.randomUUID())
                        .addValue("userId", userId)
                        .addValue("type", type)
                        .addValue("name", type + " resource")
                        .addValue("capacity", capacity == null ? null : new BigDecimal(capacity))
                        .addValue("capacityUnit", capacityUnit)
                        .addValue("capabilities", capabilities.toArray(String[]::new))
                        .addValue("available", available));
    }
}
