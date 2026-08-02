package com.foodmind.foodmindbackend.integration.agent;

import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailureCodeMapper;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP adapter for the agent native contract ({@code X-Internal-Token} auth,
 * {@code POST /internal/v1/agents/cooking-plan/generate}).
 */
@Component
public class CookingAgentHttpAdapter implements CookingAgentPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(CookingAgentHttpAdapter.class);

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

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
    public CookingAgentResult generate(AgentGeneratePlanRequest request) {
        if (!properties.isEnabled()) {
            return CookingAgentResult.failure(CookingAgentFailureCode.AGENT_DISABLED, null);
        }
        if (properties.getServiceToken() == null || properties.getServiceToken().isBlank()) {
            return CookingAgentResult.failure(CookingAgentFailureCode.CONFIGURATION_ERROR, null);
        }
        Instant startedAt = Instant.now();
        try {
            byte[] body = restClient.post()
                    .uri(properties.getEndpointPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_TOKEN_HEADER, properties.getServiceToken())
                    .header(REQUEST_ID_HEADER, request.requestId())
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length > properties.getMaxResponseBytes()) {
                log(request, CookingAgentFailureCode.OVERSIZED_RESPONSE, startedAt);
                return CookingAgentResult.failure(CookingAgentFailureCode.OVERSIZED_RESPONSE, null);
            }
            String raw = new String(body, StandardCharsets.UTF_8);
            AgentPlanResponse response = objectMapper.readValue(raw, AgentPlanResponse.class);
            if ("FAILED".equals(response.status())) {
                AgentFailedPlanResponse failed = (AgentFailedPlanResponse) response;
                CookingAgentFailureCode code = AgentFailureCodeMapper.map(failed.errorCode());
                log(request, code, startedAt);
                return CookingAgentResult.failed(failed, code, raw);
            }
            log(request, null, startedAt);
            return CookingAgentResult.of(response, raw);
        } catch (RestClientResponseException exception) {
            CookingAgentFailureCode code = switch (exception.getStatusCode().value()) {
                case 401, 403 -> CookingAgentFailureCode.INVALID_INTERNAL_CREDENTIAL;
                case 422 -> CookingAgentFailureCode.SCHEMA_MISMATCH;
                case 503 -> CookingAgentFailureCode.OVERLOADED;
                default -> CookingAgentFailureCode.NON_2XX;
            };
            log(request, code, startedAt);
            return CookingAgentResult.failure(code, null);
        } catch (ResourceAccessException exception) {
            CookingAgentFailureCode code = timeout(exception)
                    ? CookingAgentFailureCode.TIMEOUT
                    : CookingAgentFailureCode.CONNECTION_ERROR;
            log(request, code, startedAt);
            return CookingAgentResult.failure(code, null);
        } catch (RestClientException exception) {
            // RestClient wraps read/extraction I/O failures (e.g. a socket timeout during
            // content negotiation) in the generic exception type rather than ResourceAccessException.
            CookingAgentFailureCode code = timeout(exception)
                    ? CookingAgentFailureCode.TIMEOUT
                    : CookingAgentFailureCode.CONNECTION_ERROR;
            log(request, code, startedAt);
            return CookingAgentResult.failure(code, null);
        } catch (JacksonException exception) {
            log(request, CookingAgentFailureCode.MALFORMED_JSON, startedAt);
            return CookingAgentResult.failure(CookingAgentFailureCode.MALFORMED_JSON, null);
        } catch (IllegalArgumentException exception) {
            log(request, CookingAgentFailureCode.SCHEMA_MISMATCH, startedAt);
            return CookingAgentResult.failure(CookingAgentFailureCode.SCHEMA_MISMATCH, null);
        }
    }

    private boolean timeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void log(AgentGeneratePlanRequest request, CookingAgentFailureCode code, Instant startedAt) {
        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        LOGGER.info(
                "cooking_agent_call requestId={} durationMs={} failureCode={}",
                request.requestId(),
                durationMs,
                code == null ? "NONE" : code.name());
    }
}
