package com.foodmind.foodmindbackend.cooking.infrastructure.agent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskStatus;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSubmission;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentTaskException;
import com.foodmind.foodmindbackend.integration.agent.CookingAgentClientProperties;
import com.foodmind.foodmindbackend.integration.agent.CookingAgentHttpAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class CookingAgentHttpAdapterTest {

    private static final String GENERATE_PATH = "/internal/v1/agents/cooking-plan/generate";
    private static final String TASKS_PATH = "/internal/v2/cooking-plan/tasks";

    private static WireMockServer agent;
    private CookingAgentHttpAdapter adapter;
    private CookingAgentClientProperties properties;

    @BeforeAll
    static void startServer() {
        agent = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        agent.start();
    }

    @AfterAll
    static void stopServer() {
        agent.stop();
    }

    @BeforeEach
    void setUp() {
        properties = new CookingAgentClientProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(agent.baseUrl());
        properties.setEndpointPath(GENERATE_PATH);
        properties.setTasksBasePath(TASKS_PATH);
        properties.setServiceToken("test-token");
        properties.setReadTimeout(Duration.ofSeconds(30));
        properties.setMaxResponseBytes(1_048_576);
        adapter = new CookingAgentHttpAdapter(client(), properties, new ObjectMapper());
    }

    @Test
    void mapsReadyResponse() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .withHeader("X-Internal-Token", equalTo("test-token"))
                .willReturn(okJson("""
                        {"plan_id":"p-1","status":"READY","solver_status":"OPTIMAL",
                         "makespan_minutes":54,
                         "timeline":[{"task_id":"t-1","start_minute":0,"end_minute":6,"duration_minutes":6,
                                      "instruction":"Pan-fry the tofu.","dish_id":"d-1","work_mode":"ACTIVE",
                                      "category":"preparation","heat_level":"MEDIUM","resources":["stove"]}],
                         "completion_checklist":[],"mise_en_place":[],"dish_completions":[]}
                        """)));

        CookingAgentResult result = adapter.generate(request("req-1"));

        assertThat(result.successful()).isTrue();
        assertThat(((AgentReadyPlanResponse) result.response()).makespanMinutes()).isEqualTo(54);
        assertThat(((AgentReadyPlanResponse) result.response()).timeline()).hasSize(1);
    }

    @Test
    void mapsConfirmationResponse() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(okJson("""
                        {"plan_id":"p-1","status":"NEEDS_CONFIRMATION",
                         "assumptions":[{"text":"assuming 200 C for baking","confidence":"0.82","evidence":[]}],
                         "repair_options":[],"questions":[],"confirmation_questions":[],"decisions":[],
                         "plan_revision":"req-1:v1","safety_policy":null}
                        """)));

        CookingAgentResult result = adapter.generate(request("req-2"));

        assertThat(result.successful()).isTrue();
        assertThat(result.response().status()).isEqualTo("NEEDS_CONFIRMATION");
    }

    @Test
    void mapsInfeasibleResponse() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(okJson("""
                        {"plan_id":"p-1","status":"INFEASIBLE",
                         "reasons":["Insufficient 'chilli': need 60 g, have 30 g"],
                         "safe_alternatives":[]}
                        """)));

        CookingAgentResult result = adapter.generate(request("req-3"));

        assertThat(result.successful()).isTrue();
        assertThat(result.response().status()).isEqualTo("INFEASIBLE");
    }

    @Test
    void mapsBusinessFailedToFailureResult() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(okJson("""
                        {"status":"FAILED","error_code":"SCHEDULE_UNKNOWN",
                         "correlation_id":"c-1","message":"timeout"}
                        """)));

        CookingAgentResult result = adapter.generate(request("req-4"));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.SCHEDULE_UNKNOWN);
        assertThat(result.rawResponseJson()).contains("SCHEDULE_UNKNOWN");
    }

    @Test
    void mapsOverloadToRetryableFailure() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(aResponse().withStatus(503)
                        .withHeader("Retry-After", "2")
                        .withBody("{\"status\":503,\"error_code\":\"OVERLOADED\",\"correlation_id\":\"c-2\"}")));

        CookingAgentResult result = adapter.generate(request("req-5"));

        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.OVERLOADED);
    }

    @Test
    void mapsUnauthorizedToCredentialFailure() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(aResponse().withStatus(401)
                        .withBody("{\"status\":401,\"error_code\":\"AUTHENTICATION_FAILED\",\"correlation_id\":\"c-3\"}")));

        CookingAgentResult result = adapter.generate(request("req-6"));

        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.INVALID_INTERNAL_CREDENTIAL);
    }

    @Test
    void mapsValidationErrorToSchemaMismatch() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(aResponse().withStatus(422)
                        .withBody("{\"status\":422,\"error_code\":\"REQUEST_VALIDATION_ERROR\",\"correlation_id\":\"c-4\"}")));

        CookingAgentResult result = adapter.generate(request("req-7"));

        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.SCHEMA_MISMATCH);
    }

    @Test
    void mapsMalformedJson() {
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(okJson("{not-json")));

        CookingAgentResult result = adapter.generate(request("req-8"));

        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.MALFORMED_JSON);
    }

    @Test
    void mapsOversizedResponse() {
        properties.setMaxResponseBytes(16);
        adapter = new CookingAgentHttpAdapter(client(), properties, new ObjectMapper());
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(okJson("""
                        {"plan_id":"p-1","status":"READY","solver_status":"OPTIMAL",
                         "makespan_minutes":54,"timeline":[],"completion_checklist":[],
                         "mise_en_place":[],"dish_completions":[]}
                        """)));

        CookingAgentResult result = adapter.generate(request("req-9"));

        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.OVERSIZED_RESPONSE);
    }

    @Test
    void mapsReadTimeout() {
        properties.setReadTimeout(Duration.ofMillis(100));
        adapter = new CookingAgentHttpAdapter(client(), properties, new ObjectMapper());
        agent.stubFor(post(urlPathEqualTo(GENERATE_PATH))
                .willReturn(aResponse().withFixedDelay(2_000)
                        .withBody("{}")));

        CookingAgentResult result = adapter.generate(request("req-10"));

        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.TIMEOUT);
    }

    @Test
    void disabledAgentFailsFast() {
        properties.setEnabled(false);
        adapter = new CookingAgentHttpAdapter(client(), properties, new ObjectMapper());

        CookingAgentResult result = adapter.generate(request("req-11"));

        assertThat(result.failureCode()).isEqualTo(CookingAgentFailureCode.AGENT_DISABLED);
    }

    @Test
    void submitTaskMapsAcceptedSubmission() {
        agent.stubFor(post(urlPathEqualTo(TASKS_PATH))
                .withHeader("X-Internal-Token", equalTo("test-token"))
                .withHeader("X-Request-ID", equalTo("req-async-1"))
                .willReturn(aResponse().withStatus(202).withBody("""
                        {"task_id":"task-1","status":"QUEUED",
                         "location":"/internal/v2/cooking-plan/tasks/task-1",
                         "request_id":"req-async-1"}
                        """)));

        AgentTaskSubmission submission = adapter.submitTask(request("req-async-1"));

        assertThat(submission.taskId()).isEqualTo("task-1");
        assertThat(submission.status()).isEqualTo(AgentTaskStatus.QUEUED);
        assertThat(submission.location()).endsWith("/task-1");
        assertThat(submission.requestId()).isEqualTo("req-async-1");
    }

    @Test
    void submitTaskMapsConflictToConstraintConflict() {
        agent.stubFor(post(urlPathEqualTo(TASKS_PATH))
                .willReturn(aResponse().withStatus(409).withBody("{\"error_code\":\"TASK_ALREADY_EXISTS\"}")));

        assertThatThrownBy(() -> adapter.submitTask(request("req-async-2")))
                .isInstanceOfSatisfying(CookingAgentTaskException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo(CookingAgentFailureCode.CONSTRAINT_CONFLICT));
    }

    @Test
    void submitTaskMapsValidationErrorToSchemaMismatch() {
        agent.stubFor(post(urlPathEqualTo(TASKS_PATH))
                .willReturn(aResponse().withStatus(422).withBody("{\"error_code\":\"REQUEST_VALIDATION_ERROR\"}")));

        assertThatThrownBy(() -> adapter.submitTask(request("req-async-3")))
                .isInstanceOfSatisfying(CookingAgentTaskException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo(CookingAgentFailureCode.SCHEMA_MISMATCH));
    }

    @Test
    void submitTaskMapsOverloadToOverloaded() {
        agent.stubFor(post(urlPathEqualTo(TASKS_PATH))
                .willReturn(aResponse().withStatus(503).withBody("{\"error_code\":\"OVERLOADED\"}")));

        assertThatThrownBy(() -> adapter.submitTask(request("req-async-4")))
                .isInstanceOfSatisfying(CookingAgentTaskException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo(CookingAgentFailureCode.OVERLOADED));
    }

    @Test
    void submitTaskMapsReadTimeout() {
        properties.setReadTimeout(Duration.ofMillis(100));
        adapter = new CookingAgentHttpAdapter(client(), properties, new ObjectMapper());
        agent.stubFor(post(urlPathEqualTo(TASKS_PATH))
                .willReturn(aResponse().withFixedDelay(2_000).withBody("{}")));

        assertThatThrownBy(() -> adapter.submitTask(request("req-async-5")))
                .isInstanceOfSatisfying(CookingAgentTaskException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo(CookingAgentFailureCode.TIMEOUT));
    }

    @Test
    void getTaskMapsSnapshotWithProgressResultAndError() {
        agent.stubFor(get(urlPathEqualTo(TASKS_PATH + "/task-9"))
                .willReturn(okJson("""
                        {"task_id":"task-9","status":"READY","request_id":"req-9",
                         "location":"/internal/v2/cooking-plan/tasks/task-9",
                         "progress":{"node":"solve_schedule","completed_steps":7,"message":"solving"},
                         "result":{"plan_id":"p-9","status":"READY","solver_status":"OPTIMAL",
                                   "makespan_minutes":54,"timeline":[],"completion_checklist":[],
                                   "mise_en_place":[],"dish_completions":[]},
                         "error":null}
                        """)));

        AgentTaskSnapshot snapshot = adapter.getTask("task-9");

        assertThat(snapshot.taskId()).isEqualTo("task-9");
        assertThat(snapshot.status()).isEqualTo(AgentTaskStatus.READY);
        assertThat(snapshot.progress().node()).isEqualTo("solve_schedule");
        assertThat(snapshot.progress().completedSteps()).isEqualTo(7);
        assertThat(snapshot.resultJson()).isNotBlank().contains("\"status\":\"READY\"");
        assertThat(snapshot.errorJson()).isNull();
    }

    @Test
    void getTaskMapsErrorObjectToErrorJson() {
        agent.stubFor(get(urlPathEqualTo(TASKS_PATH + "/task-10"))
                .willReturn(okJson("""
                        {"task_id":"task-10","status":"FAILED","request_id":"req-10",
                         "location":"/internal/v2/cooking-plan/tasks/task-10",
                         "progress":null,
                         "result":null,
                         "error":{"status":"FAILED","error_code":"SCHEDULE_UNKNOWN",
                                  "correlation_id":"c-10","message":"timeout"}}
                        """)));

        AgentTaskSnapshot snapshot = adapter.getTask("task-10");

        assertThat(snapshot.status()).isEqualTo(AgentTaskStatus.FAILED);
        assertThat(snapshot.resultJson()).isNull();
        assertThat(snapshot.errorJson()).isNotBlank().contains("SCHEDULE_UNKNOWN");
    }

    @Test
    void getTaskMapsNotFound() {
        agent.stubFor(get(urlPathEqualTo(TASKS_PATH + "/missing"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error_code\":\"TASK_NOT_FOUND\"}")));

        assertThatThrownBy(() -> adapter.getTask("missing"))
                .isInstanceOfSatisfying(CookingAgentTaskException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo(CookingAgentFailureCode.AGENT_TASK_NOT_FOUND));
    }

    @Test
    void cancelTaskMapsCancelledSnapshot() {
        agent.stubFor(post(urlPathEqualTo(TASKS_PATH + "/task-11/cancel"))
                .willReturn(okJson("""
                        {"task_id":"task-11","status":"CANCELLED","request_id":"req-11",
                         "location":"/internal/v2/cooking-plan/tasks/task-11",
                         "progress":null,"result":null,"error":null}
                        """)));

        AgentTaskSnapshot snapshot = adapter.cancelTask("task-11");

        assertThat(snapshot.taskId()).isEqualTo("task-11");
        assertThat(snapshot.status()).isEqualTo(AgentTaskStatus.CANCELLED);
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private AgentGeneratePlanRequest request(String requestId) {
        return new AgentGeneratePlanRequest(
                requestId,
                "u-1",
                List.of(),
                List.of(),
                List.of(),
                60,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                "1.0",
                null,
                "SG");
    }
}
