package com.foodmind.foodmindbackend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionFactor;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionMode;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationDecisionProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationDecisionProfileTest {

    @Test
    void newUserWithoutAppliedPreferencesIsDefault() {
        RecommendationDecisionProfile profile = RecommendationDecisionProfile.from(List.of(), false, 0);

        assertThat(profile.mode()).isEqualTo(RecommendationDecisionMode.DEFAULT);
        assertThat(profile.appliedFactors()).isEmpty();
        assertThat(profile.groupMemberEvidenceCount()).isZero();
    }

    @Test
    void strongTasteAndAllergenUserShowsAppliedConstraints() {
        RecommendationDecisionProfile profile = RecommendationDecisionProfile.from(List.of(
                RecommendationDecisionFactor.SPICE_PREFERENCE,
                RecommendationDecisionFactor.ALLERGEN_AVOIDANCE,
                RecommendationDecisionFactor.CUISINE_PREFERENCE), false, 0);

        assertThat(profile.mode()).isEqualTo(RecommendationDecisionMode.CONSTRAINT_FOCUSED);
        assertThat(profile.appliedFactors()).containsExactly(
                RecommendationDecisionFactor.SPICE_PREFERENCE,
                RecommendationDecisionFactor.ALLERGEN_AVOIDANCE,
                RecommendationDecisionFactor.CUISINE_PREFERENCE);
    }

    @Test
    void authorizedGroupEvidenceTakesPrecedenceWithoutExposingMembers() {
        RecommendationDecisionProfile profile = RecommendationDecisionProfile.from(
                List.of(RecommendationDecisionFactor.CUISINE_PREFERENCE), true, 4);

        assertThat(profile.mode()).isEqualTo(RecommendationDecisionMode.GROUP_GUIDED);
        assertThat(profile.appliedFactors()).containsExactly(
                RecommendationDecisionFactor.CUISINE_PREFERENCE,
                RecommendationDecisionFactor.GROUP_MEMBER_RECORDS);
        assertThat(profile.groupMemberEvidenceCount()).isEqualTo(4);
    }
}
