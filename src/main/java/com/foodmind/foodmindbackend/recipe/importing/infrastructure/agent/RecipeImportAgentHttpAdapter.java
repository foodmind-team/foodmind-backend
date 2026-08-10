package com.foodmind.foodmindbackend.recipe.importing.infrastructure.agent;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.integration.agent.CookingAgentClientProperties;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportAgentPort;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportQuestion;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Component
public class RecipeImportAgentHttpAdapter implements RecipeImportAgentPort {
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final RestClient restClient;
    private final CookingAgentClientProperties properties;
    private final ObjectMapper objectMapper;

    public RecipeImportAgentHttpAdapter(
            @Qualifier("cookingAgentRestClient") RestClient restClient,
            CookingAgentClientProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Result parse(
            String requestId,
            String text,
            List<RecipeImportAnswer> answers,
            List<RecipeImportDraft> drafts,
            List<RecipeImportQuestion> questions) {
        if (!properties.isEnabled() || properties.getServiceToken() == null || properties.getServiceToken().isBlank()) {
            throw unavailable();
        }
        AgentRequest request = new AgentRequest(
                requestId,
                text,
                answers.stream().map(answer -> new AgentAnswer(answer.questionId(), answer.value())).toList(),
                drafts.stream().map(AgentDraft::fromDomain).toList(),
                questions.stream().map(AgentQuestion::fromDomain).toList());
        try {
            byte[] body = restClient.post()
                    .uri(properties.getRecipeImportPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_TOKEN_HEADER, properties.getServiceToken())
                    .header(REQUEST_ID_HEADER, requestId)
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length > properties.getMaxResponseBytes()) {
                throw unavailable();
            }
            AgentResponse response = objectMapper.readValue(body, AgentResponse.class);
            RecipeImportStatus status = RecipeImportStatus.valueOf(response.status());
            if (status != RecipeImportStatus.NEEDS_CLARIFICATION && status != RecipeImportStatus.READY) {
                throw unavailable();
            }
            return new Result(
                    status,
                    response.drafts().stream().map(AgentDraft::toDomain).toList(),
                    response.questions().stream().map(AgentQuestion::toDomain).toList());
        } catch (RestClientException | JacksonException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Recipe parsing is temporarily unavailable. Please try again.");
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record AgentRequest(
            String requestId,
            String text,
            List<AgentAnswer> answers,
            List<AgentDraft> drafts,
            List<AgentQuestion> questions) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record AgentAnswer(String questionId, String value) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record AgentResponse(String status, List<AgentDraft> drafts, List<AgentQuestion> questions) {
        private AgentResponse {
            drafts = drafts == null ? List.of() : List.copyOf(drafts);
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record AgentDraft(
            String draftId,
            String name,
            Integer servings,
            List<String> ingredients,
            List<String> steps) {
        private static AgentDraft fromDomain(RecipeImportDraft draft) {
            return new AgentDraft(
                    draft.draftId(), draft.name(), draft.servings(), draft.ingredients(), draft.steps());
        }

        private RecipeImportDraft toDomain() {
            return new RecipeImportDraft(draftId, name, servings, ingredients, steps);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record AgentQuestion(
            String questionId,
            String draftId,
            String fieldPath,
            String prompt,
            String responseType,
            boolean required,
            String suggestedValue) {
        private static AgentQuestion fromDomain(RecipeImportQuestion question) {
            return new AgentQuestion(
                    question.questionId(),
                    question.draftId(),
                    question.fieldPath(),
                    question.prompt(),
                    question.responseType(),
                    question.required(),
                    question.suggestedValue());
        }

        private RecipeImportQuestion toDomain() {
            return new RecipeImportQuestion(
                    questionId, draftId, fieldPath, prompt, responseType, required, suggestedValue);
        }
    }
}
