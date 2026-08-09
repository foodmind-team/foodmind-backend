package com.foodmind.foodmindbackend.recipe.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportAgentPort;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportQuestion;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecipeImportFlowTest extends PostgreSqlContainerSupport {
    private static final AtomicInteger AGENT_CALLS = new AtomicInteger();
    private static final AtomicBoolean AGENT_OBSERVED_TRANSACTION = new AtomicBoolean(true);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("TRUNCATE TABLE recipe_import_session, user_recipe, auth_session, app_user CASCADE");
        AGENT_CALLS.set(0);
        AGENT_OBSERVED_TRANSACTION.set(true);
    }

    @Test
    void clarificationPersistsThenAtomicallyCreatesAllRecipes() throws Exception {
        String token = read(register("import-owner@example.test", "Import Owner"), "$.accessToken");

        MvcResult created = mockMvc.perform(post("/api/v1/recipe-imports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Recipe: Lemon Pasta\\n4 servings\\nIngredients:\\n200 g spaghetti\\nSteps:\\n1. Boil the spaghetti.\\n---\\nRecipe: Tomato Salad\\nIngredients:\\n2 tomatoes\\nSteps:\\n1. Slice the tomatoes."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.drafts.length()").value(2))
                .andExpect(jsonPath("$.questions[0].questionId").value("dish-2:servings"))
                .andReturn();
        String importId = read(created, "$.importId");

        mockMvc.perform(get("/api/v1/recipe-imports/{importId}", importId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt").value("How many servings does Tomato Salad make?"));

        MvcResult ready = mockMvc.perform(post("/api/v1/recipe-imports/{importId}/answers", importId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"dish-2:servings","value":"4"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.drafts[1].servings").value(4))
                .andReturn();
        assertThat(read(ready, "$.questions").toString()).isEqualTo("[]");

        MvcResult completed = mockMvc.perform(post("/api/v1/recipe-imports/{importId}/confirm", importId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdRecipes.length()").value(2))
                .andExpect(jsonPath("$.createdRecipes[0].name").value("Lemon Pasta"))
                .andExpect(jsonPath("$.createdRecipes[1].name").value("Tomato Salad"))
                .andReturn();

        mockMvc.perform(post("/api/v1/recipe-imports/{importId}/confirm", importId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdRecipes.length()").value(2));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_recipe", Long.class)).isEqualTo(2);
        assertThat(AGENT_CALLS).hasValue(2);
        assertThat(AGENT_OBSERVED_TRANSACTION).isFalse();
        assertThat(read(completed, "$.createdRecipes[0].id")).isNotNull();
    }

    @Test
    void englishOnlyPolicyRejectsMixedInputBeforeAgentCall() throws Exception {
        String token = read(register("import-language@example.test", "Import Language"), "$.accessToken");

        mockMvc.perform(post("/api/v1/recipe-imports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Make 番茄 pasta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Please use English only. Chinese or mixed-language input is not supported."))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("ENGLISH_ONLY"));

        assertThat(AGENT_CALLS).hasValue(0);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM recipe_import_session", Long.class)).isZero();
    }

    @Test
    void sessionsAreOwnerScopedAndAnswersUseOptimisticVersions() throws Exception {
        String ownerToken = read(register("import-a@example.test", "Import A"), "$.accessToken");
        String otherToken = read(register("import-b@example.test", "Import B"), "$.accessToken");
        MvcResult created = mockMvc.perform(post("/api/v1/recipe-imports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Recipe: Soup\\nIngredients:\\n200 g peas\\nSteps:\\n1. Simmer the peas.\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String importId = read(created, "$.importId");

        mockMvc.perform(get("/api/v1/recipe-imports/{importId}", importId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/recipe-imports/{importId}/answers", importId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"dish-2:servings\",\"value\":\"2\"}]}"))
                .andExpect(status().isConflict());
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
        return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @TestConfiguration
    static class StubAgentConfiguration {
        @Bean
        @Primary
        RecipeImportAgentPort recipeImportAgentPort() {
            return (requestId, text, answers) -> {
                AGENT_CALLS.incrementAndGet();
                AGENT_OBSERVED_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
                boolean answered = answers.stream().anyMatch(answer ->
                        answer.questionId().equals("dish-2:servings") && answer.value().matches("\\d+"));
                List<RecipeImportDraft> drafts = text.contains("Lemon Pasta")
                        ? List.of(
                                draft("dish-1", "Lemon Pasta", 4, "200 g spaghetti", "Boil the spaghetti."),
                                draft("dish-2", "Tomato Salad", answered ? 4 : null, "2 tomatoes", "Slice the tomatoes."))
                        : List.of(draft("dish-1", "Soup", null, "200 g peas", "Simmer the peas."));
                String questionId = text.contains("Tomato Salad") ? "dish-2:servings" : "dish-1:servings";
                List<RecipeImportQuestion> questions = answered
                        ? List.of()
                        : List.of(new RecipeImportQuestion(
                                questionId,
                                drafts.get(drafts.size() - 1).draftId(),
                                "servings",
                                text.contains("Tomato Salad")
                                        ? "How many servings does Tomato Salad make?"
                                        : "How many servings does Soup make?",
                                "TEXT",
                                true,
                                null));
                return new RecipeImportAgentPort.Result(
                        questions.isEmpty() ? RecipeImportStatus.READY : RecipeImportStatus.NEEDS_CLARIFICATION,
                        drafts,
                        questions);
            };
        }

        private RecipeImportDraft draft(
                String draftId,
                String name,
                Integer servings,
                String ingredient,
                String step) {
            return new RecipeImportDraft(draftId, name, servings, List.of(ingredient), List.of(step));
        }
    }
}
