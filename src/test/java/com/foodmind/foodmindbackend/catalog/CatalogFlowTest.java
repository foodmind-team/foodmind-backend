package com.foodmind.foodmindbackend.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogFlowTest extends PostgreSqlContainerSupport {

    private static final String SEED_MEAL_ID = "20000000-0000-4000-8000-000000000001";
    private static final String SEED_PLACE_ID = "21000000-0000-4000-8000-000000000001";
    private static final String SEED_PRODUCT_ID = "23000000-0000-4000-8000-000000000001";
    private static final String UNKNOWN_ID = "ffffffff-ffff-4fff-8fff-ffffffffffff";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogueReadsRequireAuthenticationAndExposeReferenceChoices() throws Exception {
        mockMvc.perform(get("/api/v1/catalogue/reference-data"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String accessToken = read(register("catalogue-reference@example.test"), "$.accessToken");

        mockMvc.perform(get("/api/v1/catalogue/reference-data")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuisines[0].code").value("CHINESE"))
                .andExpect(jsonPath("$.cuisines[?(@.code == 'SINGAPOREAN')].name").value("Singaporean"))
                .andExpect(jsonPath("$.dietaryTags[?(@.code == 'VEGAN')].name").value("Vegan"))
                .andExpect(jsonPath("$.allergens[?(@.code == 'PEANUT')].name").value("Peanut"))
                .andExpect(jsonPath("$.mealTypes[0]").value("BREAKFAST"))
                .andExpect(jsonPath("$.placeTypes[0]").value("CAFE"));
    }

    @Test
    void activeMealPlaceAndProductDetailsUseSeededControlledData() throws Exception {
        String accessToken = read(register("catalogue-detail@example.test"), "$.accessToken");

        mockMvc.perform(get("/api/v1/catalogue/meals/{id}", SEED_MEAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEED_MEAL_ID))
                .andExpect(jsonPath("$.name").value("Hainanese Chicken Rice"))
                .andExpect(jsonPath("$.cuisine.code").value("SINGAPOREAN"))
                .andExpect(jsonPath("$.mealType").value("LUNCH"))
                .andExpect(jsonPath("$.allergenCodes[0]").value("GLUTEN"))
                .andExpect(jsonPath("$.offerings[0].price.currency").value("SGD"))
                .andExpect(jsonPath("$.offerings[0].place.name").isString());

        mockMvc.perform(get("/api/v1/catalogue/places/{id}", SEED_PLACE_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEED_PLACE_ID))
                .andExpect(jsonPath("$.name").value("Orchard Garden Kitchen"))
                .andExpect(jsonPath("$.coordinates.latitude").value(1.304800))
                .andExpect(jsonPath("$.observations[0].observationType").value("CLEANLINESS"))
                .andExpect(jsonPath("$.observations[0].sourceKind").value("CURATED_DEMO"))
                .andExpect(jsonPath("$.observations[0].note", containsString("not an inspection or safety certification")))
                .andExpect(jsonPath("$.offerings[0].mealName").isString());

        mockMvc.perform(get("/api/v1/catalogue/products/{id}", SEED_PRODUCT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEED_PRODUCT_ID))
                .andExpect(jsonPath("$.name").value("Unsweetened Soy Drink"))
                .andExpect(jsonPath("$.price.currency").value("SGD"))
                .andExpect(jsonPath("$.dietaryTagCodes[0]").value("VEGAN"))
                .andExpect(jsonPath("$.allergenCodes[0]").value("SOY"));
    }

    @Test
    void unknownCatalogueIdentifiersReturnSafeNotFound() throws Exception {
        String accessToken = read(register("catalogue-not-found@example.test"), "$.accessToken");

        mockMvc.perform(get("/api/v1/catalogue/meals/{id}", UNKNOWN_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Catalogue Test User",
                                  "password": "correct horse battery",
                                  "clientType": "WEB",
                                  "deviceLabel": "JUnit"
                                }
                                """.formatted(email)))
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
