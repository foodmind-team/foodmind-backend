package com.foodmind.foodmindbackend.integration.agent;

import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentGenerationResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentIngredientResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentStepResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentWarningResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentCommand;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentCookingIngredientResponse;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentCookingRequest;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentCookingResponse;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentCookingStepResponse;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentCookingWarningResponse;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@Component
public class CookingAgentHttpAdapter implements CookingAgentPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(CookingAgentHttpAdapter.class);

    private final RestClient restClient;
    private final CookingAgentClientProperties properties;
    private final ObjectMapper objectMapper;

    public CookingAgentHttpAdapter(
            @Qualifier("cookingAgentRestClient") RestClient cookingAgentRestClient,
            CookingAgentClientProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = cookingAgentRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public CookingAgentGenerationResult generate(CookingAgentCommand command) {
        if (!properties.isEnabled()) {
            return failure(command, CookingAgentFailureCode.AGENT_DISABLED, null);
        }
        if (properties.getServiceToken() == null || properties.getServiceToken().isBlank()) {
            return failure(command, CookingAgentFailureCode.CONFIGURATION_ERROR, null);
        }

        Instant startedAt = Instant.now();
        try {
            byte[] body = restClient.post()
                    .uri(properties.getEndpointPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceToken())
                    .header(CorrelationIdFilter.HEADER_NAME, command.traceId())
                    .header("X-FoodMind-Agent-Contract", command.contractVersion())
                    .body(AgentCookingRequest.from(command))
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length > properties.getMaxResponseBytes()) {
                log(command, CookingAgentFailureCode.OVERSIZED_RESPONSE, startedAt);
                return failure(command, CookingAgentFailureCode.OVERSIZED_RESPONSE, null);
            }
            AgentCookingResponse response = objectMapper.readValue(body, AgentCookingResponse.class);
            CookingAgentGenerationResult result = toResult(command, response);
            log(command, result.failureCode(), startedAt);
            return result;
        } catch (RestClientResponseException exception) {
            log(command, CookingAgentFailureCode.NON_2XX, startedAt);
            return failure(command, CookingAgentFailureCode.NON_2XX, null);
        } catch (ResourceAccessException exception) {
            CookingAgentFailureCode failureCode = timeout(exception)
                    ? CookingAgentFailureCode.TIMEOUT
                    : CookingAgentFailureCode.CONNECTION_ERROR;
            log(command, failureCode, startedAt);
            return failure(command, failureCode, null);
        } catch (JacksonException exception) {
            log(command, CookingAgentFailureCode.MALFORMED_JSON, startedAt);
            return failure(command, CookingAgentFailureCode.MALFORMED_JSON, null);
        } catch (IllegalArgumentException exception) {
            log(command, CookingAgentFailureCode.SCHEMA_MISMATCH, startedAt);
            return failure(command, CookingAgentFailureCode.SCHEMA_MISMATCH, null);
        }
    }

    private CookingAgentGenerationResult toResult(CookingAgentCommand command, AgentCookingResponse response) {
        if (response == null) {
            return failure(command, CookingAgentFailureCode.MALFORMED_JSON, null);
        }
        if (!"SUCCEEDED".equals(response.status())) {
            return CookingAgentGenerationResult.failure(
                    CookingAgentFailureCode.AGENT_UNAVAILABLE,
                    response.contractVersion(),
                    response.requestId(),
                    response.planId(),
                    response.traceId(),
                    response.agentTraceId());
        }
        return CookingAgentGenerationResult.success(
                response.contractVersion(),
                response.requestId(),
                response.planId(),
                response.traceId(),
                response.agentTraceId(),
                response.sourceRecipeId(),
                response.servings() == null ? 0 : response.servings(),
                response.totalMinutes(),
                response.estimatedCost(),
                response.currency(),
                ingredients(response.ingredients()),
                steps(response.steps()),
                warnings(response.warnings()));
    }

    private List<CookingAgentIngredientResult> ingredients(List<AgentCookingIngredientResponse> ingredients) {
        if (ingredients == null) {
            return List.of();
        }
        return ingredients.stream()
                .map(ingredient -> new CookingAgentIngredientResult(
                        ingredient.sequenceNo() == null ? 0 : ingredient.sequenceNo(),
                        ingredient.ingredientName(),
                        ingredient.quantity(),
                        ingredient.unit(),
                        ingredient.availability()))
                .toList();
    }

    private List<CookingAgentStepResult> steps(List<AgentCookingStepResponse> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .map(step -> new CookingAgentStepResult(
                        step.stepNo() == null ? 0 : step.stepNo(),
                        step.instruction()))
                .toList();
    }

    private List<CookingAgentWarningResult> warnings(List<AgentCookingWarningResponse> warnings) {
        if (warnings == null) {
            return List.of();
        }
        return warnings.stream()
                .map(warning -> new CookingAgentWarningResult(
                        warning.sequenceNo() == null ? 0 : warning.sequenceNo(),
                        warning.warningCode(),
                        warning.message()))
                .toList();
    }

    private boolean timeout(ResourceAccessException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CookingAgentGenerationResult failure(
            CookingAgentCommand command,
            CookingAgentFailureCode failureCode,
            String agentTraceId) {
        return CookingAgentGenerationResult.failure(
                failureCode,
                attemptedContractVersion(failureCode, command),
                command.requestId(),
                command.planId(),
                command.traceId(),
                agentTraceId);
    }

    private String attemptedContractVersion(CookingAgentFailureCode failureCode, CookingAgentCommand command) {
        return failureCode == CookingAgentFailureCode.AGENT_DISABLED
                || failureCode == CookingAgentFailureCode.CONFIGURATION_ERROR
                ? null
                : command.contractVersion();
    }

    private void log(CookingAgentCommand command, CookingAgentFailureCode failureCode, Instant startedAt) {
        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        LOGGER.info(
                "cooking_agent_call planId={} traceId={} durationMs={} contractVersion={} failureCode={}",
                command.planId(),
                command.traceId(),
                durationMs,
                command.contractVersion(),
                failureCode == null ? "NONE" : failureCode.name());
    }
}
