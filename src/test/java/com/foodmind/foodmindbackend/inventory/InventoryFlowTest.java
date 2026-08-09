package com.foodmind.foodmindbackend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryFlowTest extends PostgreSqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("TRUNCATE TABLE inventory_lot, auth_session, app_user CASCADE");
    }

    @Test
    void fullCrudIsOwnerScopedVersionedAndArchivesInsteadOfDeleting() throws Exception {
        String ownerToken = token(register("inventory-owner@example.test", "Inventory Owner"));
        String otherToken = token(register("inventory-other@example.test", "Inventory Other"));

        MvcResult created = mockMvc.perform(post("/api/v1/inventory/lots")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lotRequest("Firm tofu", "300", "g", "2026-08-12")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.ingredientName").value("Firm tofu"))
                .andExpect(jsonPath("$.available").value(300))
                .andReturn();
        String lotId = read(created, "$.lotId");

        mockMvc.perform(get("/api/v1/inventory/lots/{lotId}", lotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/inventory/lots/{lotId}", lotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lotRequest("Firm tofu", "400", "g", "2026-08-14")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.quantity").value(400))
                .andExpect(jsonPath("$.expiryDate").value("2026-08-14"));

        mockMvc.perform(put("/api/v1/inventory/lots/{lotId}", lotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lotRequest("Firm tofu", "500", "g", null)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/inventory/lots/{lotId}", lotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/inventory/lots/{lotId}", lotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/inventory/lots")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        Long archived = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_lot WHERE id = ? AND archived_at IS NOT NULL",
                Long.class, UUID.fromString(lotId));
        assertThat(archived).isEqualTo(1);
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

    private String lotRequest(String ingredientName, String quantity, String unit, String expiryDate) {
        String expiry = expiryDate == null ? "null" : "\"" + expiryDate + "\"";
        return """
                {
                  "ingredientName": "%s",
                  "quantity": %s,
                  "unit": "%s",
                  "expiryDate": %s
                }
                """.formatted(ingredientName, quantity, unit, expiry);
    }

    private String token(MvcResult result) throws Exception {
        return read(result, "$.accessToken");
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
