package com.foodmind.foodmindbackend.integration.agent;

import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailureCodeMapper;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRecipeInput;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskProgress;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskStatus;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSubmission;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentTaskException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
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
        String raw = null;
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
            raw = new String(body, StandardCharsets.UTF_8);
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
            LOGGER.error(
                    "cooking_agent_response_parse_failed requestId={} rawPreview={}",
                    request.requestId(),
                    String.valueOf(raw).length() > 200 ? String.valueOf(raw).substring(0, 200) : raw,
                    exception);
            log(request, CookingAgentFailureCode.MALFORMED_JSON, startedAt);
            return CookingAgentResult.failure(CookingAgentFailureCode.MALFORMED_JSON, null);
        } catch (IllegalArgumentException exception) {
            log(request, CookingAgentFailureCode.SCHEMA_MISMATCH, startedAt);
            return CookingAgentResult.failure(CookingAgentFailureCode.SCHEMA_MISMATCH, null);
        }
    }

    @Override
    public List<Map<String, Object>> preprocess(List<AgentRecipeInput> recipes) {
        if (!properties.isEnabled()) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.AGENT_DISABLED);
        }
        if (properties.getServiceToken() == null || properties.getServiceToken().isBlank()) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.CONFIGURATION_ERROR);
        }
        try {
            Map<String, Object> body = Map.of(
                    "request_id", UUID.randomUUID().toString(),
                    "recipes", recipes);
            byte[] raw = restClient.post()
                    .uri(properties.getPreprocessPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_TOKEN_HEADER, properties.getServiceToken())
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            String response = requireBody(raw);
            JsonNode root = objectMapper.readTree(response);
            JsonNode recipesNode = root.path("recipes");
            if (!recipesNode.isArray()) {
                throw new CookingAgentTaskException(CookingAgentFailureCode.MALFORMED_JSON);
            }
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (JsonNode node : recipesNode) {
                candidates.add(objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {}));
            }
            return candidates;
        } catch (RestClientResponseException exception) {
            throw new CookingAgentTaskException(httpFailureCode(exception.getStatusCode().value()));
        } catch (ResourceAccessException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        } catch (RestClientException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        } catch (JacksonException exception) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.MALFORMED_JSON);
        }
    }

    @Override
    public AgentTaskSubmission submitTask(AgentGeneratePlanRequest request) {
        if (!properties.isEnabled()) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.AGENT_DISABLED);
        }
        if (properties.getServiceToken() == null || properties.getServiceToken().isBlank()) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.CONFIGURATION_ERROR);
        }
        try {
            byte[] body = restClient.post()
                    .uri(properties.getTasksBasePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_TOKEN_HEADER, properties.getServiceToken())
                    .header(REQUEST_ID_HEADER, request.requestId())
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            String raw = requireBody(body);
            return objectMapper.readValue(raw, AgentTaskSubmission.class);
        } catch (RestClientResponseException exception) {
            throw new CookingAgentTaskException(httpFailureCode(exception.getStatusCode().value()));
        } catch (ResourceAccessException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        } catch (RestClientException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        } catch (JacksonException exception) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.MALFORMED_JSON);
        } catch (IllegalArgumentException exception) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.SCHEMA_MISMATCH);
        }
    }

    @Override
    public AgentTaskSnapshot getTask(String taskId) {
        try {
            byte[] body = restClient.get()
                    .uri(properties.getTasksBasePath() + "/" + taskId)
                    .header(INTERNAL_TOKEN_HEADER, properties.getServiceToken())
                    .retrieve()
                    .body(byte[].class);
            return parseTaskSnapshot(requireBody(body));
        } catch (RestClientResponseException exception) {
            throw new CookingAgentTaskException(httpFailureCode(exception.getStatusCode().value()));
        } catch (ResourceAccessException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        } catch (RestClientException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        }
    }

    @Override
    public AgentTaskSnapshot cancelTask(String taskId) {
        try {
            byte[] body = restClient.post()
                    .uri(properties.getTasksBasePath() + "/" + taskId + "/cancel")
                    .header(INTERNAL_TOKEN_HEADER, properties.getServiceToken())
                    .retrieve()
                    .body(byte[].class);
            return parseTaskSnapshot(requireBody(body));
        } catch (RestClientResponseException exception) {
            throw new CookingAgentTaskException(httpFailureCode(exception.getStatusCode().value()));
        } catch (ResourceAccessException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        } catch (RestClientException exception) {
            throw new CookingAgentTaskException(accessFailureCode(exception));
        }
    }

    private CookingAgentFailureCode httpFailureCode(int status) {
        return switch (status) {
            case 401, 403 -> CookingAgentFailureCode.INVALID_INTERNAL_CREDENTIAL;
            case 404 -> CookingAgentFailureCode.AGENT_TASK_NOT_FOUND;
            case 409 -> CookingAgentFailureCode.CONSTRAINT_CONFLICT;
            case 422 -> CookingAgentFailureCode.SCHEMA_MISMATCH;
            case 503 -> CookingAgentFailureCode.OVERLOADED;
            default -> CookingAgentFailureCode.NON_2XX;
        };
    }

    private CookingAgentFailureCode accessFailureCode(Throwable exception) {
        return timeout(exception)
                ? CookingAgentFailureCode.TIMEOUT
                : CookingAgentFailureCode.CONNECTION_ERROR;
    }

    private String requireBody(byte[] body) {
        if (body == null || body.length > properties.getMaxResponseBytes()) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.OVERSIZED_RESPONSE);
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private AgentTaskSnapshot parseTaskSnapshot(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            AgentTaskStatus status = AgentTaskStatus.valueOf(root.path("status").asText());
            JsonNode progressNode = root.get("progress");
            AgentTaskProgress progress = progressNode == null || progressNode.isNull()
                    ? null
                    : objectMapper.treeToValue(progressNode, AgentTaskProgress.class);
            return new AgentTaskSnapshot(
                    text(root, "task_id"),
                    status,
                    text(root, "request_id"),
                    text(root, "location"),
                    progress,
                    writeOrNull(root.get("result")),
                    writeOrNull(root.get("error")));
        } catch (JacksonException exception) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.MALFORMED_JSON);
        } catch (IllegalArgumentException exception) {
            throw new CookingAgentTaskException(CookingAgentFailureCode.SCHEMA_MISMATCH);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String writeOrNull(JsonNode node) throws JacksonException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.writeValueAsString(node);
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
