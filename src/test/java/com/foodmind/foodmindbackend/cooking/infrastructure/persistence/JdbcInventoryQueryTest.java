package com.foodmind.foodmindbackend.cooking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInventoryLotSnapshot;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootTest
class JdbcInventoryQueryTest extends PostgreSqlContainerSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcInventoryQuery query;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("TRUNCATE TABLE inventory_lot, inventory_item, app_user CASCADE",
                new MapSqlParameterSource());
        query = new JdbcInventoryQuery(jdbcTemplate);
    }

    @Test
    void returnsUsableLotsInFefoOrderOnlyForOwner() {
        UUID owner = insertUser("owner@example.test");
        UUID other = insertUser("other@example.test");
        UUID item = insertItem("chicken breast");

        UUID lotEarly = insertLot(item, owner, "500", "100", LocalDate.of(2026, 8, 10));
        UUID lotLate = insertLot(item, owner, "300", "0", LocalDate.of(2026, 9, 1));
        insertLot(item, owner, "50", "50", LocalDate.of(2026, 8, 5)); // fully reserved -> excluded
        insertLot(item, other, "999", "0", LocalDate.of(2026, 8, 1)); // other user -> excluded

        List<AgentInventoryLotSnapshot> lots = query.lots(owner);

        assertThat(lots).extracting(AgentInventoryLotSnapshot::lotId)
                .containsExactly(lotEarly.toString(), lotLate.toString());
        assertThat(lots.get(0).canonicalName()).isEqualTo("chicken breast");
        assertThat(lots.get(0).onHand()).isEqualByComparingTo("500");
        assertThat(lots.get(0).reserved()).isEqualByComparingTo("100");
        assertThat(lots.get(0).expiryDate()).isEqualTo(LocalDate.of(2026, 8, 10));
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

    private UUID insertItem(String canonicalName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_item (id, canonical_name, default_unit)
                VALUES (:id, :name, 'g')
                """,
                new MapSqlParameterSource("id", id).addValue("name", canonicalName));
        return id;
    }

    private UUID insertLot(UUID itemId, UUID userId, String onHand, String reserved, LocalDate expiry) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_lot (id, item_id, user_id, on_hand, reserved, unit, expiry_date)
                VALUES (:id, :itemId, :userId, :onHand, :reserved, 'g', :expiry)
                """,
                new MapSqlParameterSource("id", id)
                        .addValue("itemId", itemId)
                        .addValue("userId", userId)
                        .addValue("onHand", new BigDecimal(onHand))
                        .addValue("reserved", new BigDecimal(reserved))
                        .addValue("expiry", expiry));
        return id;
    }
}
