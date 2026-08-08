package com.foodmind.foodmindbackend.cooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.cooking.application.CookingTaskPollingCoordinator;
import com.foodmind.foodmindbackend.cooking.application.port.CookingAgentPort;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentCompletionItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentConfirmationQuestion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDecision;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentDishCompletion;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentFailedPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentGeneratePlanRequest;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentInfeasiblePlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentMiseEnPlaceItem;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentQuestionOption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentReadyPlanResponse;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentRepairOption;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskProgress;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSnapshot;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskStatus;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTaskSubmission;
import com.foodmind.foodmindbackend.cooking.domain.agent.AgentTimelineTask;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentFailureCode;
import com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentResult;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end cooking-plan flow against the agent-native contract: submit ->
 * PROCESSING -> one of the four terminal states persisted -> GET read-back,
 * plus idempotent replay and ownership isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "foodmind.cooking.task.poll-enabled=true",
        "foodmind.cooking.task.poll-interval=1h"
})
class CookingPlanFlowTest extends PostgreSqlContainerSupport {

    private static final AtomicReference<Function<AgentGeneratePlanRequest, CookingAgentResult>> AGENT_RESPONSE =
            new AtomicReference<>(CookingPlanFlowTest::readyAgentResult);
    private static final AtomicReference<Function<AgentGeneratePlanRequest, AgentTaskSubmission>> SUBMIT_RESPONSE =
            new AtomicReference<>(request -> new AgentTaskSubmission(
                    "task-async-1", AgentTaskStatus.QUEUED,
                    "/internal/v2/cooking-plan/tasks/task-async-1", request.requestId()));
    private static final AtomicReference<Function<String, AgentTaskSnapshot>> TASK_SNAPSHOT =
            new AtomicReference<>(CookingPlanFlowTest::runningTaskSnapshot);
    private static final AtomicBoolean REMOTE_CALL_OBSERVED_TRANSACTION = new AtomicBoolean(true);
    private static final AtomicInteger AGENT_CALL_COUNT = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CookingTaskPollingCoordinator pollingCoordinator;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE idempotency_record, cooking_plan, auth_session, app_user CASCADE
                """);
        AGENT_RESPONSE.set(CookingPlanFlowTest::readyAgentResult);
        SUBMIT_RESPONSE.set(request -> new AgentTaskSubmission(
                "task-async-1", AgentTaskStatus.QUEUED,
                "/internal/v2/cooking-plan/tasks/task-async-1", request.requestId()));
        TASK_SNAPSHOT.set(CookingPlanFlowTest::runningTaskSnapshot);
        REMOTE_CALL_OBSERVED_TRANSACTION.set(true);
        AGENT_CALL_COUNT.set(0);
    }

    @Test
    void generatePersistsReadyPlanOutsideTransactionAndReadsBackTimeline() throws Exception {
        String accessToken = read(register("cooking-success@example.test", "Cooking Success"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-success-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.solverStatus").value("OPTIMAL"))
                .andExpect(jsonPath("$.makespanMinutes").value(54))
                .andExpect(jsonPath("$.timeline[0].instruction").value("Pan-fry the tofu."))
                .andExpect(jsonPath("$.completionChecklist[0].ingredientName").value("chilli"))
                .andReturn();

        assertThat(REMOTE_CALL_OBSERVED_TRANSACTION).isFalse();
        assertThat(AGENT_CALL_COUNT).hasValue(1);
        String planId = read(result, "$.planId");

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.timeline[0].startMinute").value(0))
                .andExpect(jsonPath("$.timeline[0].workMode").value("ACTIVE"))
                .andExpect(jsonPath("$.miseEnPlace[0].operation").value("dice"))
                .andExpect(jsonPath("$.dishCompletions[0].completionMinute").value(54));

        mockMvc.perform(get("/api/v1/cooking-plans/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].planId").value(planId))
                .andExpect(jsonPath("$.items[0].status").value("READY"))
                .andExpect(jsonPath("$.items[0].sourceCount").isNumber())
                .andExpect(jsonPath("$.items[0].taskCount").value(1));
    }

    @Test
    void replayWithSameIdempotencyKeyReturnsSamePlanWithoutSecondAgentCall() throws Exception {
        String accessToken = read(register("cooking-replay@example.test", "Cooking Replay"), "$.accessToken");

        MvcResult first = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = read(first, "$.planId");

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId));

        assertThat(AGENT_CALL_COUNT).hasValue(1);
        Long planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM cooking_plan", Long.class);
        assertThat(planCount).isEqualTo(1);
    }

    @Test
    void needsConfirmationPersistsAndReadsBackQuestions() throws Exception {
        AGENT_RESPONSE.set(request -> confirmationAgentResult(request));
        String accessToken = read(register("cooking-confirm@example.test", "Cooking Confirm"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-confirm-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_CONFIRMATION"))
                .andExpect(jsonPath("$.planRevision").value(org.hamcrest.Matchers.endsWith(":v1")))
                .andExpect(jsonPath("$.questions[0]").value("Would you like to proceed?"))
                .andExpect(jsonPath("$.confirmationQuestions[0].questionId").value("q-1"))
                .andReturn();
        String planId = read(result, "$.planId");

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CONFIRMATION"))
                .andExpect(jsonPath("$.assumptions[0].text").value("assuming 200 C"))
                .andExpect(jsonPath("$.repairOptions[0].optionId").value("opt-1"))
                .andExpect(jsonPath("$.confirmationQuestions[0].options[0].value").value("accept"))
                .andExpect(jsonPath("$.decisions[0].optionId").value("opt-1"));
    }

    @Test
    void confirmationResubmissionFlowSubmitsDecisionsAndGeneratesNewRevision() throws Exception {
        AtomicReference<AgentGeneratePlanRequest> lastRequest = new AtomicReference<>();
        AGENT_RESPONSE.set(request -> {
            lastRequest.set(request);
            if (request.planRevision() == null) {
                return confirmationAgentResult(request);
            }
            return readyAgentResult(request);
        });
        String accessToken = read(register("cooking-decide@example.test", "Cooking Decide"), "$.accessToken");

        MvcResult confirmation = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "decide-gen-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_CONFIRMATION"))
                .andReturn();
        String confirmationPlanId = read(confirmation, "$.planId");
        String revision = read(confirmation, "$.planRevision");

        MvcResult decided = mockMvc.perform(post("/api/v1/cooking-plans/{planId}/decisions", confirmationPlanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "decide-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  { "questionId": "q-1", "value": "accept" },
                                  { "questionId": "repair:opt-1", "value": "opt-1" }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.planId").value(org.hamcrest.Matchers.not(confirmationPlanId)))
                .andReturn();
        String newPlanId = read(decided, "$.planId");

        // The resubmission carried the incremented revision and the mapped decision.
        assertThat(lastRequest.get().planRevision()).isEqualTo(revision.replace(":v1", ":v2"));
        assertThat(lastRequest.get().approvedDecisions()).hasSize(1);
        assertThat(lastRequest.get().approvedDecisions().get(0).optionId()).isEqualTo("opt-1");
        assertThat(lastRequest.get().approvedDecisions().get(0).optionType()).isEqualTo("reduce_servings");
        assertThat(lastRequest.get().approvedDecisions().get(0).planRevision())
                .isEqualTo(revision.replace(":v1", ":v2"));

        // The original confirmation plan is untouched; the new plan is readable.
        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", confirmationPlanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CONFIRMATION"));
        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", newPlanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        // Idempotent replay of the same decision key returns the same new plan without another agent call.
        AGENT_CALL_COUNT.set(0);
        mockMvc.perform(post("/api/v1/cooking-plans/{planId}/decisions", confirmationPlanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "decide-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  { "questionId": "q-1", "value": "accept" },
                                  { "questionId": "repair:opt-1", "value": "opt-1" }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(newPlanId));
        assertThat(AGENT_CALL_COUNT).hasValue(0);
    }

    @Test
    void decisionsOnNonConfirmationPlanConflicts() throws Exception {
        String accessToken = read(register("cooking-stale@example.test", "Cooking Stale"), "$.accessToken");
        String planId = read(mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "stale-gen-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andReturn(), "$.planId");

        mockMvc.perform(post("/api/v1/cooking-plans/{planId}/decisions", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "stale-decide-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [ { "questionId": "q-1", "value": "accept" } ]
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void invalidDecisionsRejectedWithoutDirtyData() throws Exception {
        AGENT_RESPONSE.set(request -> confirmationAgentResult(request));
        String accessToken = read(register("cooking-bad@example.test", "Cooking Bad"), "$.accessToken");
        String planId = read(mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "bad-gen-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andReturn(), "$.planId");

        mockMvc.perform(post("/api/v1/cooking-plans/{planId}/decisions", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "bad-decide-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [ { "questionId": "unknown-q", "value": "accept" } ]
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Long planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM cooking_plan", Long.class);
        assertThat(planCount).isEqualTo(1);
    }

    @Test
    void infeasiblePersistsAndReadsBackReasons() throws Exception {
        AGENT_RESPONSE.set(request -> {
            AgentInfeasiblePlanResponse infeasible = new AgentInfeasiblePlanResponse(request.requestId(), "INFEASIBLE",
                    List.of("Insufficient 'chilli': need 60 g, have 30 g"), List.of("Use dried chilli"));
            return CookingAgentResult.of(infeasible, json(infeasible));
        });
        String accessToken = read(register("cooking-infeasible@example.test", "Cooking Infeasible"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-infeasible-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.reasons[0]").value("Insufficient 'chilli': need 60 g, have 30 g"))
                .andReturn();
        String planId = read(result, "$.planId");

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.safeAlternatives[0]").value("Use dried chilli"));
    }

    @Test
    void businessFailedPersistsAndReadsBackError() throws Exception {
        AGENT_RESPONSE.set(request -> new CookingAgentResult(
                new AgentFailedPlanResponse("FAILED", "SCHEDULE_UNKNOWN", "c-1", "timeout"),
                CookingAgentFailureCode.SCHEDULE_UNKNOWN, null));
        String accessToken = read(register("cooking-failed@example.test", "Cooking Failed"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-failed-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_UNKNOWN"))
                .andExpect(jsonPath("$.errorMessage").value("timeout"))
                .andReturn();
        String planId = read(result, "$.planId");

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_UNKNOWN"))
                .andExpect(jsonPath("$.errorMessage").value("timeout"));
    }

    @Test
    void ownerIsolationReturnsNotFound() throws Exception {
        String ownerToken = read(register("cooking-owner@example.test", "Cooking Owner"), "$.accessToken");
        String otherToken = read(register("cooking-other@example.test", "Cooking Other"), "$.accessToken");
        String planId = read(mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header("Idempotency-Key", "owner-plan-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated())
                .andReturn(), "$.planId");

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void noControlledRecipeMatchRejectsWithValidationError() throws Exception {
        String accessToken = read(register("cooking-nomatch@example.test", "Cooking Nomatch"), "$.accessToken");

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-nomatch-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noMatchRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Select at least one saved recipe."));
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadConflicts() throws Exception {
        String accessToken = read(register("cooking-conflict@example.test", "Cooking Conflict"), "$.accessToken");

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/cooking-plans/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "cook-conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chickpeaRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void asyncGenerateReturns202ThenPollingMaterialisesReadyPlan() throws Exception {
        String accessToken = read(register("cooking-async@example.test", "Cooking Async"), "$.accessToken");

        MvcResult accepted = mockMvc.perform(post("/api/v1/cooking-plans/generate-async")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "async-ready-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.taskId").value("task-async-1"))
                .andExpect(jsonPath("$.location").value(org.hamcrest.Matchers.endsWith("/task")))
                .andReturn();
        String planId = read(accepted, "$.planId");

        // Submission ran outside any database transaction; task handle is persisted.
        assertThat(REMOTE_CALL_OBSERVED_TRANSACTION).isFalse();
        assertThat(AGENT_CALL_COUNT).hasValue(1);
        String agentTaskId = jdbcTemplate.queryForObject(
                "SELECT agent_task_id FROM cooking_plan WHERE id = ?", String.class, UUID.fromString(planId));
        assertThat(agentTaskId).isEqualTo("task-async-1");

        // The first poll (RUNNING) mirrors progress and renews the lease.
        pollingCoordinator.pollDueTasks();
        mockMvc.perform(get("/api/v1/cooking-plans/{planId}/task", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-async-1"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.syncState").value("PENDING"))
                .andExpect(jsonPath("$.progress.node").value("solve_schedule"))
                .andExpect(jsonPath("$.progress.completedSteps").value(7));

        // The next poll (READY) materialises the terminal state.
        forceDuePoll(UUID.fromString(planId));
        TASK_SNAPSHOT.set(CookingPlanFlowTest::readyTaskSnapshot);
        pollingCoordinator.pollDueTasks();

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.makespanMinutes").value(54));
        // Terminal plans are no longer served by the task endpoint.
        mockMvc.perform(get("/api/v1/cooking-plans/{planId}/task", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void asyncReplayWithSameKeyReturnsSamePlanWithoutSecondSubmission() throws Exception {
        String accessToken = read(register("cooking-async-replay@example.test", "Cooking Async Replay"), "$.accessToken");

        MvcResult first = mockMvc.perform(post("/api/v1/cooking-plans/generate-async")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "async-replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isAccepted())
                .andReturn();
        String planId = read(first, "$.planId");

        // Poll to a terminal state, then replay: same plan, no new submission.
        forceDuePoll(UUID.fromString(planId));
        TASK_SNAPSHOT.set(CookingPlanFlowTest::readyTaskSnapshot);
        pollingCoordinator.pollDueTasks();
        AGENT_CALL_COUNT.set(0);

        mockMvc.perform(post("/api/v1/cooking-plans/generate-async")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "async-replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.taskId").value("task-async-1"));

        assertThat(AGENT_CALL_COUNT).hasValue(0);
        Long planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM cooking_plan", Long.class);
        assertThat(planCount).isEqualTo(1);
    }

    @Test
    void asyncSubmitFailureReturnsTerminalFailedPlanWith200() throws Exception {
        SUBMIT_RESPONSE.set(request -> {
            throw new com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentTaskException(
                    CookingAgentFailureCode.OVERLOADED);
        });
        String accessToken = read(register("cooking-async-fail@example.test", "Cooking Async Fail"), "$.accessToken");

        MvcResult result = mockMvc.perform(post("/api/v1/cooking-plans/generate-async")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "async-fail-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("OVERLOADED"))
                .andReturn();
        String planId = read(result, "$.planId");

        // The failed submission must not leave a pollable generation row behind.
        Long generationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cooking_plan_generation WHERE plan_id = ?", Long.class, UUID.fromString(planId));
        assertThat(generationCount).isZero();
        mockMvc.perform(get("/api/v1/cooking-plans/{planId}/task", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void asyncPollingFailureBeyondMaxAttemptsFailsPlan() throws Exception {
        String accessToken = read(register("cooking-async-pollfail@example.test", "Cooking Async PollFail"), "$.accessToken");
        MvcResult accepted = mockMvc.perform(post("/api/v1/cooking-plans/generate-async")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "async-pollfail-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isAccepted())
                .andReturn();
        String planId = read(accepted, "$.planId");

        TASK_SNAPSHOT.set(taskId -> {
            throw new com.foodmind.foodmindbackend.cooking.domain.agent.CookingAgentTaskException(
                    CookingAgentFailureCode.TIMEOUT);
        });
        for (int attempt = 0; attempt < 5; attempt++) {
            forceDuePoll(UUID.fromString(planId));
            pollingCoordinator.pollDueTasks();
        }

        mockMvc.perform(get("/api/v1/cooking-plans/{planId}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("TIMEOUT"));
    }

    @Test
    void asyncCancelMarksPlanFailedWithoutDirtyData() throws Exception {
        String accessToken = read(register("cooking-async-cancel@example.test", "Cooking Async Cancel"), "$.accessToken");
        MvcResult accepted = mockMvc.perform(post("/api/v1/cooking-plans/generate-async")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Idempotency-Key", "async-cancel-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tofuRequest()))
                .andExpect(status().isAccepted())
                .andReturn();
        String planId = read(accepted, "$.planId");

        TASK_SNAPSHOT.set(CookingPlanFlowTest::runningTaskSnapshot);
        mockMvc.perform(post("/api/v1/cooking-plans/{planId}/cancel", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("TASK_CANCELLED"));
        String syncState = jdbcTemplate.queryForObject(
                "SELECT sync_state FROM cooking_plan_generation WHERE plan_id = ?", String.class, UUID.fromString(planId));
        assertThat(syncState).isEqualTo("CANCELLED");

        // Cancel is idempotent in effect: a second call conflicts (plan no longer PROCESSING).
        mockMvc.perform(post("/api/v1/cooking-plans/{planId}/cancel", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/cooking-plans/{planId}/task", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound());
    }

    private static CookingAgentResult readyAgentResult(AgentGeneratePlanRequest request) {
        AgentReadyPlanResponse ready = new AgentReadyPlanResponse(
                request.requestId(), "READY", "OPTIMAL", 54,
                List.of(new AgentTimelineTask("t-1", 0, 6, 6, "Pan-fry the tofu.", "d-1", "ACTIVE",
                        "preparation", "MEDIUM", List.of("stove"), null, null)),
                List.of(new AgentCompletionItem("c-1", "chilli", List.of("d-1"), List.of())),
                List.of(new AgentMiseEnPlaceItem("dice: chicken breast", "chicken breast", "dice", 6,
                        List.of("knife"), "diced_chicken")),
                List.of(new AgentDishCompletion("d-1", 54, 9, false)),
                null, null, null);
        return CookingAgentResult.of(ready, null);
    }

    private static CookingAgentResult confirmationAgentResult(AgentGeneratePlanRequest request) {
        String revision = request.requestId() + ":v1";
        AgentConfirmationPlanResponse confirmation = new AgentConfirmationPlanResponse(
                request.requestId(), "NEEDS_CONFIRMATION",
                List.of(new com.foodmind.foodmindbackend.cooking.domain.agent.AgentAssumption(
                        "assuming 200 C", new java.math.BigDecimal("0.82"), List.of())),
                List.of(new AgentRepairOption("opt-1", "reduce_servings", "Reduce to 2 servings",
                        List.of("servings 4 -> 2"), List.of("feasible"), "validated")),
                List.of("Would you like to proceed?"),
                List.of(
                        new AgentConfirmationQuestion("q-1", "recipe.r-1.assumptions", "Accept?",
                                "CHOICE", List.of(new AgentQuestionOption("accept", "Accept", true)), true, "200 C"),
                        new AgentConfirmationQuestion("repair:opt-1", "repair_options",
                                "Apply the repair option 'Reduce to 2 servings'?",
                                "CHOICE",
                                List.of(new AgentQuestionOption("opt-1", "Apply", true),
                                        new AgentQuestionOption("__skip__", "Do not apply", false)),
                                false, "opt-1")),
                List.of(new AgentDecision("opt-1", "reduce_servings", Map.of("servings", 2), revision)),
                revision, null);
        return CookingAgentResult.of(confirmation, json(confirmation));
    }

    private static AgentTaskSnapshot runningTaskSnapshot(String taskId) {
        return new AgentTaskSnapshot(taskId, AgentTaskStatus.RUNNING, null, null,
                new AgentTaskProgress("solve_schedule", 7, "solving"), null, null);
    }

    private static AgentTaskSnapshot readyTaskSnapshot(String taskId) {
        AgentReadyPlanResponse ready = readyPlan(taskId);
        return new AgentTaskSnapshot(taskId, AgentTaskStatus.READY, null, null,
                new AgentTaskProgress("finalise", 9, null), json(ready), null);
    }

    private static AgentReadyPlanResponse readyPlan(String planId) {
        return new AgentReadyPlanResponse(
                planId, "READY", "OPTIMAL", 54,
                List.of(new AgentTimelineTask("t-1", 0, 6, 6, "Pan-fry the tofu.", "d-1", "ACTIVE",
                        "preparation", "MEDIUM", List.of("stove"), null, null)),
                List.of(new AgentCompletionItem("c-1", "chilli", List.of("d-1"), List.of())),
                List.of(new AgentMiseEnPlaceItem("dice: chicken breast", "chicken breast", "dice", 6,
                        List.of("knife"), "diced_chicken")),
                List.of(new AgentDishCompletion("d-1", 54, 9, false)),
                null, null, null);
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Failed to serialise test agent response.", exception);
        }
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

    private String tofuRequest() {
        return """
                {
                  "ingredients": [
                    { "ingredientName": "Firm tofu", "quantity": 300, "unit": "g", "source": "MANUAL" },
                    { "ingredientName": "Fresh ginger", "quantity": 15, "unit": "g", "source": "MANUAL" }
                  ],
                  "servings": 2,
                  "maxMinutes": 60,
                  "maxBudget": 20,
                  "currency": "SGD",
                  "requiredDietaryTagCodes": ["VEGAN"],
                  "avoidAllergenCodes": []
                }
                """;
    }

    private String chickpeaRequest() {
        return """
                {
                  "ingredients": [
                    { "ingredientName": "Chickpeas", "quantity": 480, "unit": "g", "source": "MANUAL" }
                  ],
                  "servings": 4,
                  "maxMinutes": 60,
                  "maxBudget": 20,
                  "currency": "SGD",
                  "requiredDietaryTagCodes": ["VEGAN"],
                  "avoidAllergenCodes": []
                }
                """;
    }

    private String noMatchRequest() {
        return """
                {
                  "ingredients": [
                    { "ingredientName": "Dragonfruit", "quantity": 1, "unit": "item", "source": "MANUAL" }
                  ],
                  "servings": 2,
                  "maxMinutes": 20,
                  "maxBudget": 5,
                  "currency": "SGD",
                  "requiredDietaryTagCodes": ["VEGAN"],
                  "avoidAllergenCodes": []
                }
                """;
    }

    private void forceDuePoll(UUID planId) {
        jdbcTemplate.update("""
                UPDATE cooking_plan_generation
                SET next_poll_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE plan_id = ?
                """, planId);
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @TestConfiguration
    static class StubAgentConfiguration {

        @Bean
        @Primary
        CookingAgentPort cookingAgentPort() {
            return new CookingAgentPort() {
                @Override
                public CookingAgentResult generate(AgentGeneratePlanRequest request) {
                    AGENT_CALL_COUNT.incrementAndGet();
                    REMOTE_CALL_OBSERVED_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return AGENT_RESPONSE.get().apply(request);
                }

                @Override
                public AgentTaskSubmission submitTask(AgentGeneratePlanRequest request) {
                    AGENT_CALL_COUNT.incrementAndGet();
                    REMOTE_CALL_OBSERVED_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return SUBMIT_RESPONSE.get().apply(request);
                }

                @Override
                public AgentTaskSnapshot getTask(String taskId) {
                    REMOTE_CALL_OBSERVED_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return TASK_SNAPSHOT.get().apply(taskId);
                }

                @Override
                public AgentTaskSnapshot cancelTask(String taskId) {
                    REMOTE_CALL_OBSERVED_TRANSACTION.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return TASK_SNAPSHOT.get().apply(taskId);
                }
            };
        }
    }
}
