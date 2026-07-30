package com.foodmind.foodmindbackend.integration.agent;

import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentRecommendationCandidateResponse;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentRecommendationRequest;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentRecommendationResponse;
import com.foodmind.foodmindbackend.recommendation.application.port.RecommendationAgentPort;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentCandidateResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @date: 30/07/2026 10:14 am
 */

@Component
public class RecommendationAgentHttpAdapter implements RecommendationAgentPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationAgentHttpAdapter.class);

    private final RestClient restClient;
    private final AgentClientProperties properties;
    private final ObjectMapper objectMapper;

    public RecommendationAgentHttpAdapter(
            RestClient recommendationAgentRestClient,
            AgentClientProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = recommendationAgentRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentGenerationResult generate(RecommendationAgentCommand command) {
        if (!properties.isEnabled()) {
            return failure(command, AgentFailureCode.AGENT_DISABLED, null);
        }
        if (properties.getServiceToken() == null || properties.getServiceToken().isBlank()) {
            return failure(command, AgentFailureCode.CONFIGURATION_ERROR, null);
        }

        Instant startedAt = Instant.now();
        try {
            byte[] body = restClient.post()
                    .uri(properties.getEndpointPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceToken())
                    .header(CorrelationIdFilter.HEADER_NAME, command.traceId())
                    .header("X-FoodMind-Agent-Contract", command.contractVersion())
                    .body(AgentRecommendationRequest.from(command))
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length > properties.getMaxResponseBytes()) {
                log(command, AgentFailureCode.OVERSIZED_RESPONSE, null, startedAt);
                return failure(command, AgentFailureCode.OVERSIZED_RESPONSE, null);
            }
            AgentRecommendationResponse response = objectMapper.readValue(body, AgentRecommendationResponse.class);
            AgentGenerationResult result = toResult(command, response);
            log(command, result.failureCode(), response == null ? null : response.modelVersion(), startedAt);
            return result;
        } catch (RestClientResponseException exception) {
            log(command, AgentFailureCode.NON_2XX, null, startedAt);
            return failure(command, AgentFailureCode.NON_2XX, null);
        } catch (ResourceAccessException exception) {
            AgentFailureCode failureCode = timeout(exception) ? AgentFailureCode.TIMEOUT : AgentFailureCode.CONNECTION_ERROR;
            log(command, failureCode, null, startedAt);
            return failure(command, failureCode, null);
        } catch (JacksonException exception) {
            log(command, AgentFailureCode.MALFORMED_JSON, null, startedAt);
            return failure(command, AgentFailureCode.MALFORMED_JSON, null);
        } catch (IllegalArgumentException exception) {
            log(command, AgentFailureCode.SCHEMA_MISMATCH, null, startedAt);
            return failure(command, AgentFailureCode.SCHEMA_MISMATCH, null);
        }
    }

    private AgentGenerationResult toResult(RecommendationAgentCommand command, AgentRecommendationResponse response) {
        if (response == null) {
            return failure(command, AgentFailureCode.MALFORMED_JSON, null);
        }
        if (!"SUCCEEDED".equals(response.status())) {
            return AgentGenerationResult.failure(
                    AgentFailureCode.INFERENCE_UNAVAILABLE,
                    response.contractVersion(),
                    response.requestId(),
                    response.sessionId(),
                    response.traceId(),
                    response.agentTraceId());
        }
        return AgentGenerationResult.success(
                response.contractVersion(),
                response.requestId(),
                response.sessionId(),
                response.traceId(),
                response.agentTraceId(),
                response.status(),
                response.modelVersion(),
                response.featureSchemaVersion(),
                candidates(response.candidates()));
    }

    private List<AgentCandidateResult> candidates(List<AgentRecommendationCandidateResponse> candidates) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> new AgentCandidateResult(
                        candidate.candidateId(),
                        candidate.rank() == null ? 0 : candidate.rank(),
                        type(candidate.recommendationType()),
                        candidate.modelScore(),
                        reasonCodes(candidate.reasonCodes()),
                        candidate.explanation(),
                        candidate.featureSnapshot()))
                .toList();
    }

    private RecommendationType type(String value) {
        return value == null ? null : RecommendationType.valueOf(value);
    }

    private List<ReasonCode> reasonCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(ReasonCode::valueOf).toList();
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

    private AgentGenerationResult failure(
            RecommendationAgentCommand command,
            AgentFailureCode failureCode,
            String agentTraceId) {
        return AgentGenerationResult.failure(
                failureCode,
                attemptedContractVersion(failureCode, command),
                command.requestId(),
                command.sessionId(),
                command.traceId(),
                agentTraceId);
    }

    private String attemptedContractVersion(AgentFailureCode failureCode, RecommendationAgentCommand command) {
        return failureCode == AgentFailureCode.AGENT_DISABLED || failureCode == AgentFailureCode.CONFIGURATION_ERROR
                ? null
                : command.contractVersion();
    }

    private void log(
            RecommendationAgentCommand command,
            AgentFailureCode failureCode,
            String modelVersion,
            Instant startedAt) {
        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        LOGGER.info(
                "recommendation_agent_call sessionId={} traceId={} durationMs={} contractVersion={} modelVersion={} failureCode={}",
                command.sessionId(),
                command.traceId(),
                durationMs,
                command.contractVersion(),
                modelVersion,
                failureCode == null ? "NONE" : failureCode.name());
    }
}
