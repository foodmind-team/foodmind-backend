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
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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

    @Test
    void jambalayaIngredientsMergeByTrimmedCaseInsensitiveName() throws Exception {
        String accessToken = token(register("jambalaya-inventory@example.test", "Jambalaya Inventory"));
        List<IngredientFixture> ingredients = List.of(
                new IngredientFixture("vegetable oil", "1", "tbsp"),
                new IngredientFixture("bacon", "180", "g"),
                new IngredientFixture("andouille or smoked sausage", "200", "g"),
                new IngredientFixture("chicken thigh", "300", "g"),
                new IngredientFixture("prawns/shrimp", "12", "piece"),
                new IngredientFixture("garlic", "4", "clove"),
                new IngredientFixture("butter", "15", "g"),
                new IngredientFixture("onion", "1", "piece"),
                new IngredientFixture("celery", "2", "rib"),
                new IngredientFixture("green capsicum / bell pepper", "2", "piece"),
                new IngredientFixture("long grain rice", "1.25", "cup"),
                new IngredientFixture("low-sodium chicken broth / stock", "625", "ml"),
                new IngredientFixture("crushed canned tomato", "200", "g"),
                new IngredientFixture("tomato paste", "2", "tbsp"),
                new IngredientFixture("green onions", "1", "cup"),
                new IngredientFixture("fresh thyme", "2", "tsp"),
                new IngredientFixture("sweet paprika", "4", "tsp"),
                new IngredientFixture("garlic powder", "1", "tsp"),
                new IngredientFixture("onion powder", "1", "tsp"),
                new IngredientFixture("cayenne powder", "0.5", "tsp"),
                new IngredientFixture("black pepper", "0.5", "tsp"),
                new IngredientFixture("salt", "0.5", "tsp"));

        for (IngredientFixture ingredient : ingredients) {
            MvcResult first = createLot(accessToken, ingredient.name(), ingredient.quantity(), ingredient.unit())
                    .andExpect(status().isCreated())
                    .andReturn();
            String lotId = read(first, "$.lotId");

            createLot(
                            accessToken,
                            "  " + ingredient.name().toUpperCase(Locale.ROOT) + "  ",
                            ingredient.quantity(),
                            ingredient.unit())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lotId").value(lotId));

            BigDecimal onHand = jdbcTemplate.queryForObject(
                    "SELECT on_hand FROM inventory_lot WHERE id = ?",
                    BigDecimal.class,
                    UUID.fromString(lotId));
            assertThat(onHand).isEqualByComparingTo(
                    new BigDecimal(ingredient.quantity()).multiply(new BigDecimal("2")));
        }

        mockMvc.perform(get("/api/v1/inventory/lots")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(ingredients.size()))
                .andExpect(jsonPath("$.items.length()").value(ingredients.size()));
    }

    private org.springframework.test.web.servlet.ResultActions createLot(
            String accessToken,
            String ingredientName,
            String quantity,
            String unit) throws Exception {
        return mockMvc.perform(post("/api/v1/inventory/lots")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(lotRequest(ingredientName, quantity, unit, null)));
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

    private record IngredientFixture(String name, String quantity, String unit) {
    }
}
