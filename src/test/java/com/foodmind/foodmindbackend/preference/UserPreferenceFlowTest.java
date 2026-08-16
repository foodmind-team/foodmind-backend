package com.foodmind.foodmindbackend.preference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
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
 * @date: 29/7/2026 8:55 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserPreferenceFlowTest extends PostgreSqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE auth_session, user_cuisine_preference, user_dietary_tag,
                    user_allergen, user_preferred_meal_type, user_preference, app_user CASCADE
                """);
    }

    @Test
    void getAndReplacePreferencesUseOwnerPrincipalAndDeterministicCodeOrdering() throws Exception {
        String primaryToken = read(register("primary-pref@example.test", "Primary Preference"), "$.accessToken");
        String secondaryToken = read(register("secondary-pref@example.test", "Secondary Preference"), "$.accessToken");

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("SGD"))
                .andExpect(jsonPath("$.cookingRegion").value("SG"))
                .andExpect(jsonPath("$.cleanlinessPriority").value(0))
                .andExpect(jsonPath("$.likedCuisineCodes").isEmpty())
                .andExpect(jsonPath("$.hardConstraints.requiredDietaryTagCodes").isEmpty());

        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primaryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budgetMin": 3.50,
                                  "budgetMax": 12.00,
                                  "currency": "sgd",
                                  "spiceTolerance": 4,
                                  "preferredArea": "  Clementi  ",
                                  "preferredLatitude": 1.315000,
                                  "preferredLongitude": 103.765000,
                                  "maxDistanceKm": 5.50,
                                  "cleanlinessPriority": 3,
                                  "minimumCleanlinessEvidenceScore": 0.75,
                                  "foodGoal": "balanced",
                                  "drinkSweetnessPreference": "less_sweet",
                                  "drinkIcePreference": "no_ice",
                                  "cookingRegion": "us",
                                  "likedCuisineCodes": ["japanese", "CHINESE"],
                                  "dislikedCuisineCodes": ["MALAY"],
                                  "dietaryTagCodes": ["vegetarian", "VEGAN"],
                                  "allergens": [
                                    { "code": "peanut", "severity": "severe" },
                                    { "code": "soy", "severity": "avoid" }
                                  ],
                                  "preferredMealTypes": ["dinner", "lunch"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetMin").value(3.50))
                .andExpect(jsonPath("$.budgetMax").value(12.00))
                .andExpect(jsonPath("$.currency").value("SGD"))
                .andExpect(jsonPath("$.cookingRegion").value("US"))
                .andExpect(jsonPath("$.preferredArea").value("Clementi"))
                .andExpect(jsonPath("$.likedCuisineCodes[0]").value("CHINESE"))
                .andExpect(jsonPath("$.likedCuisineCodes[1]").value("JAPANESE"))
                .andExpect(jsonPath("$.dislikedCuisineCodes[0]").value("MALAY"))
                .andExpect(jsonPath("$.dietaryTagCodes[0]").value("VEGAN"))
                .andExpect(jsonPath("$.dietaryTagCodes[1]").value("VEGETARIAN"))
                .andExpect(jsonPath("$.allergens[0].code").value("PEANUT"))
                .andExpect(jsonPath("$.allergens[0].severity").value("SEVERE"))
                .andExpect(jsonPath("$.allergens[1].code").value("SOY"))
                .andExpect(jsonPath("$.preferredMealTypes[0]").value("DINNER"))
                .andExpect(jsonPath("$.preferredMealTypes[1]").value("LUNCH"))
                .andExpect(jsonPath("$.hardConstraints.requiredDietaryTagCodes[0]").value("VEGAN"))
                .andExpect(jsonPath("$.hardConstraints.allergens[0].code").value("PEANUT"));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedCuisineCodes").isEmpty())
                .andExpect(jsonPath("$.cookingRegion").value("SG"))
                .andExpect(jsonPath("$.allergens").isEmpty());
    }

    @Test
    void focusedCookingRegionUpdateSynchronisesWithoutReplacingOtherPreferences() throws Exception {
        String accessToken = read(register("cooking-region@example.test", "Cooking Region"), "$.accessToken");

        putPreferences(accessToken, """
                {
                  "budgetMax": 30.00,
                  "currency": "SGD",
                  "dietaryTagCodes": ["VEGAN"],
                  "allergens": [
                    { "code": "PEANUT", "severity": "SEVERE" }
                  ]
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cookingRegion").value("SG"));

        mockMvc.perform(put("/api/v1/users/me/preferences/cooking-region")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "cookingRegion": "cn" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cookingRegion").value("CN"))
                .andExpect(jsonPath("$.budgetMax").value(30.00))
                .andExpect(jsonPath("$.dietaryTagCodes[0]").value("VEGAN"))
                .andExpect(jsonPath("$.allergens[0].code").value("PEANUT"));

        putPreferences(accessToken, """
                {
                  "budgetMax": 40.00,
                  "currency": "SGD"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cookingRegion").value("CN"))
                .andExpect(jsonPath("$.budgetMax").value(40.00));

        mockMvc.perform(put("/api/v1/users/me/preferences/cooking-region")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "cookingRegion": "AU" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void replacePreferencesRejectsInvalidRangesContradictionsAndUnknownCodes() throws Exception {
        String accessToken = read(register("invalid-pref@example.test", "Invalid Preference"), "$.accessToken");

        putPreferences(accessToken, """
                {
                  "budgetMin": 15.00,
                  "budgetMax": 10.00,
                  "currency": "ZZZ",
                  "spiceTolerance": 6,
                  "preferredLatitude": 1.30,
                  "maxDistanceKm": 5.00,
                  "cleanlinessPriority": 9,
                  "minimumCleanlinessEvidenceScore": 1.50
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].message", containsString("Budget minimum")))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("currency"));

        putPreferences(accessToken, """
                {
                  "likedCuisineCodes": ["CHINESE"],
                  "dislikedCuisineCodes": ["chinese"]
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("CONTRADICTORY_CUISINE"));

        putPreferences(accessToken, """
                {
                  "likedCuisineCodes": ["MARTIAN"],
                  "dietaryTagCodes": ["VEGAN"]
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("likedCuisineCodes"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("UNKNOWN_REFERENCE_CODE"))
                .andExpect(jsonPath("$.fieldErrors[0].message", containsString("MARTIAN")));
    }

    @Test
    void failedFullReplacementPreservesPreviousAggregate() throws Exception {
        String accessToken = read(register("rollback-pref@example.test", "Rollback Preference"), "$.accessToken");

        putPreferences(accessToken, """
                {
                  "budgetMin": 4.00,
                  "budgetMax": 8.00,
                  "likedCuisineCodes": ["INDIAN"],
                  "dietaryTagCodes": ["VEGAN"],
                  "allergens": [
                    { "code": "MILK", "severity": "AVOID" }
                  ]
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedCuisineCodes[0]").value("INDIAN"));

        putPreferences(accessToken, """
                {
                  "budgetMin": 1.00,
                  "budgetMax": 2.00,
                  "likedCuisineCodes": ["UNKNOWN"]
                }
                """)
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetMin").value(4.00))
                .andExpect(jsonPath("$.budgetMax").value(8.00))
                .andExpect(jsonPath("$.likedCuisineCodes[0]").value("INDIAN"))
                .andExpect(jsonPath("$.dietaryTagCodes[0]").value("VEGAN"))
                .andExpect(jsonPath("$.allergens[0].code").value("MILK"))
                .andExpect(jsonPath("$.allergens[0].severity").value("AVOID"))
                .andExpect(jsonPath("$.likedCuisineCodes[0]", not("UNKNOWN")));
    }

    private org.springframework.test.web.servlet.ResultActions putPreferences(String accessToken, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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
