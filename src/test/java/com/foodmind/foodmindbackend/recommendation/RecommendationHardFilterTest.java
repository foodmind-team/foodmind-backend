package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.filter.FilterCode;
import com.foodmind.foodmindbackend.recommendation.domain.filter.HardFilterPipeline;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

class RecommendationHardFilterTest {

    private final HardFilterPipeline pipeline = new HardFilterPipeline();

    @Test
    void appliesFirstStableHardFilterCode() {
        assertThat(pipeline.evaluate(request(List.of("SOY"), List.of()), preferences(), candidate()).filterCode())
                .isEqualTo(FilterCode.ALLERGEN);
    }

    @Test
    void missingCleanlinessEvidenceCannotSatisfyThreshold() {
        CandidateEvidence candidate = candidateWithoutCleanliness();

        assertThat(pipeline.evaluate(request(List.of(), List.of()), preferencesWithCleanlinessThreshold(), candidate).filterCode())
                .isEqualTo(FilterCode.CLEANLINESS_EVIDENCE);
    }

    private RecommendationRequestContext request(List<String> avoidAllergens, List<String> requiredDietary) {
        return new RecommendationRequestContext(
                null,
                "DINNER",
                new BigDecimal("20.00"),
                "SGD",
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2030-07-30T12:00:00Z"),
                avoidAllergens,
                requiredDietary,
                4,
                null);
    }

    private PreferenceEvidence preferences() {
        return new PreferenceEvidence(
                new BigDecimal("20.00"),
                "SGD",
                4,
                null,
                null,
                null,
                null,
                null,
                List.of("INDIAN"),
                List.of(),
                List.of(),
                List.of(),
                List.of("DINNER"));
    }

    private PreferenceEvidence preferencesWithCleanlinessThreshold() {
        return new PreferenceEvidence(
                new BigDecimal("20.00"),
                "SGD",
                4,
                null,
                null,
                null,
                null,
                new BigDecimal("0.80"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private CandidateEvidence candidate() {
        return new CandidateEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tofu Bowl",
                "DINNER",
                "INDIAN",
                UUID.randomUUID(),
                "Fixture Place",
                "Serangoon",
                null,
                null,
                new MoneyAmount(new BigDecimal("10.00"), "SGD"),
                1,
                true,
                new com.foodmind.foodmindbackend.recommendation.domain.CleanlinessEvidence(
                        new BigDecimal("0.90"),
                        OffsetDateTime.parse("2030-07-01T00:00:00Z"),
                        "CURATED_DEMO"),
                List.of("VEGAN"),
                List.of("SOY"),
                false,
                0,
                null,
                null,
                0,
                null,
                null,
                null);
    }

    private CandidateEvidence candidateWithoutCleanliness() {
        CandidateEvidence base = candidate();
        return new CandidateEvidence(
                base.placeMealId(),
                base.mealId(),
                base.mealName(),
                base.mealType(),
                base.cuisineCode(),
                base.placeId(),
                base.placeName(),
                base.area(),
                base.latitude(),
                base.longitude(),
                base.price(),
                base.spiceLevel(),
                base.available(),
                null,
                base.dietaryTagCodes(),
                List.of(),
                base.wantToTry(),
                base.personalRecordCount(),
                base.personalAverageRating(),
                base.lastPersonalRecordAt(),
                base.groupRecordCount(),
                base.groupAverageRating(),
                base.lastGroupRecordAt(),
                base.distanceKm());
    }
}
