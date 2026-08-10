package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.integration.agent.dto.AgentRecommendationCandidateResponse;
import com.foodmind.foodmindbackend.integration.agent.dto.AgentRecommendationResponse;
import com.foodmind.foodmindbackend.recommendation.domain.AgentResultValidator;
import com.foodmind.foodmindbackend.recommendation.domain.AgentResultValidator.AgentValidationException;
import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CleanlinessEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentCandidateResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentFailureCode;
import com.foodmind.foodmindbackend.recommendation.domain.agent.AgentGenerationResult;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.agent.RecommendationAgentCommand;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 10:14 am
 */

class AgentResultValidatorTest {

    private static final UUID REQUEST_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final String TRACE_ID = "30000000-0000-4000-8000-000000000003";
    private static final UUID FIRST_CANDIDATE_ID = UUID.fromString("30000000-0000-4000-8000-000000000101");
    private static final UUID SECOND_CANDIDATE_ID = UUID.fromString("30000000-0000-4000-8000-000000000102");
    private static final UUID THIRD_CANDIDATE_ID = UUID.fromString("30000000-0000-4000-8000-000000000103");

    private final AgentResultValidator validator = new AgentResultValidator();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void acceptsFrozenValidFixture() throws Exception {
        AgentGenerationResult result = resultFromFixture("valid-normal-response.json");

        assertThat(validator.validate(command(), result).candidates())
                .extracting("candidateId")
                .containsExactly(FIRST_CANDIDATE_ID, SECOND_CANDIDATE_ID, THIRD_CANDIDATE_ID);
    }

    @Test
    void rejectsReturnedCandidateNotInEligibleCommand() throws Exception {
        AgentGenerationResult result = resultFromFixture("invalid-unknown-id-response.json");

        assertThatThrownBy(() -> validator.validate(command(), result))
                .isInstanceOf(AgentValidationException.class)
                .extracting("failureCode")
                .isEqualTo(AgentFailureCode.UNKNOWN_ID);
    }

    @Test
    void rejectsUnsupportedFeatureSchemaVersion() throws Exception {
        AgentGenerationResult result = resultFromFixture("valid-normal-response.json");
        AgentGenerationResult unsupported = AgentGenerationResult.success(
                result.contractVersion(),
                result.requestId(),
                result.sessionId(),
                result.traceId(),
                result.agentTraceId(),
                result.modelStatus(),
                result.modelVersion(),
                "recommendation-features-v99",
                result.candidates());

        assertThatThrownBy(() -> validator.validate(command(), unsupported))
                .isInstanceOf(AgentValidationException.class)
                .extracting("failureCode")
                .isEqualTo(AgentFailureCode.UNSUPPORTED_VERSION);
    }

    @Test
    void rejectsReasonCodeUnsupportedByStoredEvidence() {
        AgentGenerationResult result = AgentGenerationResult.success(
                AgentResultValidator.SUPPORTED_CONTRACT_VERSION,
                REQUEST_ID,
                SESSION_ID,
                TRACE_ID,
                "agent-trace-invalid-reason",
                "SUCCEEDED",
                "recommendation-agent-demo-2026-07-30",
                AgentResultValidator.FEATURE_SCHEMA_VERSION,
                List.of(new AgentCandidateResult(
                        FIRST_CANDIDATE_ID,
                        1,
                        RecommendationType.PERSONAL,
                        new BigDecimal("0.8000000"),
                        List.of(ReasonCode.WANT_TO_TRY),
                        "This should be rejected because no want-to-try evidence exists.",
                        Map.of())));

        assertThatThrownBy(() -> validator.validate(command(), result))
                .isInstanceOf(AgentValidationException.class)
                .extracting("failureCode")
                .isEqualTo(AgentFailureCode.INVALID_REASON);
    }

    @Test
    void rejectsUnsafeExplanationClaims() {
        AgentGenerationResult result = AgentGenerationResult.success(
                AgentResultValidator.SUPPORTED_CONTRACT_VERSION,
                REQUEST_ID,
                SESSION_ID,
                TRACE_ID,
                "agent-trace-unsafe-claim",
                "SUCCEEDED",
                "recommendation-agent-demo-2026-07-30",
                AgentResultValidator.FEATURE_SCHEMA_VERSION,
                List.of(new AgentCandidateResult(
                        FIRST_CANDIDATE_ID,
                        1,
                        RecommendationType.PERSONAL,
                        new BigDecimal("0.8000000"),
                        List.of(ReasonCode.CUISINE_MATCH),
                        "Guaranteed allergen-free meal for medical needs.",
                        Map.of())));

        assertThatThrownBy(() -> validator.validate(command(), result))
                .isInstanceOf(AgentValidationException.class)
                .extracting("failureCode")
                .isEqualTo(AgentFailureCode.SCHEMA_MISMATCH);
    }

    private RecommendationAgentCommand command() {
        return new RecommendationAgentCommand(
                AgentResultValidator.SUPPORTED_CONTRACT_VERSION,
                REQUEST_ID,
                SESSION_ID,
                TRACE_ID,
                OffsetDateTime.parse("2030-07-30T12:00:02Z"),
                Map.of("mealType", "DINNER"),
                Map.of("currency", "SGD"),
                List.of(
                        agentCandidate(FIRST_CANDIDATE_ID, "INDIAN", null, 0, false, 0, null),
                        agentCandidate(SECOND_CANDIDATE_ID, "JAPANESE", new BigDecimal("1.4"), 0, false, 0, null),
                        agentCandidate(THIRD_CANDIDATE_ID, "INDIAN", null, 0, false, 2, new BigDecimal("4.70"))));
    }

    private RecommendationAgentCandidate agentCandidate(
            UUID candidateId,
            String cuisineCode,
            BigDecimal distanceKm,
            int personalRecordCount,
            boolean wantToTry,
            int groupRecordCount,
            BigDecimal groupAverageRating) {
        CandidateEvidence evidence = new CandidateEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Fixture Meal",
                "DINNER",
                cuisineCode,
                UUID.randomUUID(),
                "Fixture Place",
                "Serangoon",
                new BigDecimal("1.349600"),
                new BigDecimal("103.873700"),
                new MoneyAmount(new BigDecimal("9.50"), "SGD"),
                2,
                true,
                new CleanlinessEvidence(new BigDecimal("0.90"), OffsetDateTime.parse("2030-07-01T00:00:00Z"), "CURATED_DEMO"),
                List.of("VEGETARIAN"),
                List.of(),
                wantToTry,
                personalRecordCount,
                null,
                null,
                groupRecordCount,
                groupAverageRating,
                null,
                distanceKm);
        return new RecommendationAgentCandidate(candidateId, evidence.sourceKey(), evidence, Map.of("cuisineCode", cuisineCode));
    }

    private AgentGenerationResult resultFromFixture(String fixtureName) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/contracts/agent/recommendation/" + fixtureName)) {
            AgentRecommendationResponse response = objectMapper.readValue(inputStream, AgentRecommendationResponse.class);
            return AgentGenerationResult.success(
                    response.contractVersion(),
                    response.requestId(),
                    response.sessionId(),
                    response.traceId(),
                    response.agentTraceId(),
                    response.status(),
                    response.modelVersion(),
                    response.featureSchemaVersion(),
                    response.candidates().stream()
                            .map(this::candidateResult)
                            .toList());
        }
    }

    private AgentCandidateResult candidateResult(AgentRecommendationCandidateResponse candidate) {
        return new AgentCandidateResult(
                candidate.candidateId(),
                candidate.rank(),
                RecommendationType.valueOf(candidate.recommendationType()),
                candidate.modelScore(),
                candidate.reasonCodes().stream().map(ReasonCode::valueOf).toList(),
                candidate.explanation(),
                candidate.featureSnapshot());
    }
}
