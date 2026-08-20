package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CleanlinessEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.fallback.FallbackSelector;
import com.foodmind.foodmindbackend.recommendation.domain.filter.FilterCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FallbackSelectorTest {

    private final FallbackSelector selector = new FallbackSelector();

    @Test
    void wantToTryCandidateWinsFallbackOrderingEvenWithALowerBaseScore() {
        CandidateEvidence wantToTry = candidate("Want to Try", true, new BigDecimal("20"), 4, new BigDecimal("8"), null);
        CandidateEvidence otherwiseHigher = candidate("Highly Rated", false, new BigDecimal("4"), 1, new BigDecimal("1"), null);

        assertThat(selector.select(List.of(new EvaluatedCandidate(otherwiseHigher, null), new EvaluatedCandidate(wantToTry, null)), preferences()))
                .first()
                .extracting(item -> item.candidate().evidence().mealName())
                .isEqualTo("Want to Try");
    }

    @Test
    void missingOrUnmetSoftPreferencesStayEligibleAndMatchingEvidenceRanksHigher() {
        CandidateEvidence matching = candidate("Matching", false, new BigDecimal("8"), 1, new BigDecimal("1"), new BigDecimal("0.95"));
        CandidateEvidence missing = candidate("Missing", false, null, null, null, null);
        RecommendationRequestContext request = new RecommendationRequestContext(
                null, null, "DINNER", new BigDecimal("10"), "SGD", null, null, null, new BigDecimal("5"), null,
                OffsetDateTime.parse("2030-07-30T12:00:00Z"), List.of(), List.of(), 2, new BigDecimal("0.80"));

        assertThat(selector.select(List.of(new EvaluatedCandidate(missing, null), new EvaluatedCandidate(matching, null)), request, preferences()))
                .first()
                .extracting(item -> item.candidate().evidence().mealName())
                .isEqualTo("Matching");
    }

    @Test
    void relaxesOnlyDislikedCuisineAndRecentRepeatWhenNothingIsEligible() {
        CandidateEvidence relaxed = candidate("Relaxed", false, new BigDecimal("8"), 1, new BigDecimal("1"), null);
        CandidateEvidence allergen = candidate("Unsafe", true, new BigDecimal("4"), 1, new BigDecimal("1"), null);

        assertThat(selector.select(
                        List.of(new EvaluatedCandidate(relaxed, FilterCode.DISLIKED_CUISINE),
                                new EvaluatedCandidate(allergen, FilterCode.ALLERGEN)),
                        preferences()))
                .extracting(item -> item.candidate().evidence().mealName())
                .containsExactly("Relaxed");
        assertThat(selector.select(List.of(new EvaluatedCandidate(allergen, FilterCode.ALLERGEN)), preferences())).isEmpty();
    }

    private PreferenceEvidence preferences() {
        return new PreferenceEvidence(
                new BigDecimal("10"), "SGD", 2, null, null, null, new BigDecimal("5"), new BigDecimal("0.80"),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private CandidateEvidence candidate(
            String mealName,
            boolean wantToTry,
            BigDecimal price,
            Integer spiceLevel,
            BigDecimal distanceKm,
            BigDecimal cleanlinessScore) {
        return new CandidateEvidence(
                UUID.randomUUID(), UUID.randomUUID(), mealName, "DINNER", "ASIAN", UUID.randomUUID(), "Fixture Place", null,
                null, null, price == null ? null : new MoneyAmount(price, "SGD"), spiceLevel, true,
                cleanlinessScore == null ? null : new CleanlinessEvidence(cleanlinessScore, OffsetDateTime.parse("2030-07-01T00:00:00Z"), "TEST"),
                List.of(), List.of(), wantToTry, 0, null, null, 0, null, null, distanceKm);
    }
}
