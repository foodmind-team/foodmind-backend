package com.foodmind.foodmindbackend.recommendation.domain.fallback;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CandidateSourceType;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonCode;
import com.foodmind.foodmindbackend.recommendation.domain.reason.ReasonTemplateRenderer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class FallbackSelector {

    private static final BigDecimal MAX_SCORE = new BigDecimal("0.9999999");
    private final ReasonTemplateRenderer reasonRenderer = new ReasonTemplateRenderer();

    public List<SelectedCandidate> select(List<EvaluatedCandidate> evaluatedCandidates, PreferenceEvidence preferences) {
        List<EvaluatedCandidate> eligible = evaluatedCandidates.stream()
                .filter(EvaluatedCandidate::eligible)
                .toList();
        List<SelectedCandidate> selected = new ArrayList<>();
        Set<String> usedOfferings = new HashSet<>();

        // A saved record is an explicit product promise: reserve one returned card whenever one
        // survived the same hard filters as catalogue candidates.
        eligible.stream()
                .filter(candidate -> candidate.evidence().sourceType() == CandidateSourceType.FOOD_RECORD)
                .sorted(comparatorFor(RecommendationType.PERSONAL, preferences))
                .findFirst()
                .ifPresent(candidate -> {
                    RecommendationType type = candidate.evidence().groupRecordCount() > 0
                            ? RecommendationType.GROUP_INSPIRED : RecommendationType.PERSONAL;
                    selected.add(toSelected(candidate, preferences, type, 1));
                });
        selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));

        pick(eligible, preferences, RecommendationType.PERSONAL, usedOfferings)
                .ifPresent(candidate -> selected.add(toSelected(candidate, preferences, RecommendationType.PERSONAL, selected.size() + 1)));
        selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));

        pick(eligible, preferences, RecommendationType.EXPLORATORY, usedOfferings)
                .ifPresent(candidate -> selected.add(toSelected(candidate, preferences, RecommendationType.EXPLORATORY, selected.size() + 1)));
        selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));

        pick(eligible, preferences, RecommendationType.GROUP_INSPIRED, usedOfferings)
                .ifPresent(candidate -> selected.add(toSelected(candidate, preferences, RecommendationType.GROUP_INSPIRED, selected.size() + 1)));

        if (selected.size() < 3) {
            usedOfferings.clear();
            selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));
            eligible.stream()
                    .filter(candidate -> !usedOfferings.contains(candidate.evidence().sourceKey()))
                    .sorted(comparatorFor(RecommendationType.PERSONAL, preferences))
                    .limit(3 - selected.size())
                    .forEach(candidate -> selected.add(toSelected(candidate, preferences, RecommendationType.PERSONAL, selected.size() + 1)));
        }
        return selected;
    }

    private java.util.Optional<EvaluatedCandidate> pick(
            List<EvaluatedCandidate> eligible,
            PreferenceEvidence preferences,
            RecommendationType type,
            Set<String> usedOfferings) {
        boolean hasGroupEvidence = eligible.stream().anyMatch(candidate -> candidate.evidence().groupRecordCount() > 0);
        return eligible.stream()
                .filter(candidate -> !usedOfferings.contains(candidate.evidence().sourceKey()))
                .filter(candidate -> matchesType(candidate, preferences, type, hasGroupEvidence))
                .sorted(comparatorFor(type, preferences))
                .findFirst();
    }

    private boolean matchesType(
            EvaluatedCandidate candidate,
            PreferenceEvidence preferences,
            RecommendationType type,
            boolean hasGroupEvidence) {
        CandidateEvidence evidence = candidate.evidence();
        return switch (type) {
            case GROUP_INSPIRED -> evidence.groupRecordCount() > 0;
            case EXPLORATORY -> evidence.personalRecordCount() == 0
                    && !preferences.likedCuisineCodes().contains(evidence.cuisineCode())
                    && (!hasGroupEvidence || evidence.groupRecordCount() == 0);
            case PERSONAL -> !hasGroupEvidence || evidence.groupRecordCount() == 0;
        };
    }

    private Comparator<EvaluatedCandidate> comparatorFor(RecommendationType type, PreferenceEvidence preferences) {
        return Comparator
                .comparing((EvaluatedCandidate candidate) -> score(candidate.evidence(), preferences, type)).reversed()
                .thenComparing(candidate -> budgetFit(candidate.evidence(), preferences))
                .thenComparing(candidate -> distanceFit(candidate.evidence()))
                .thenComparing(candidate -> candidate.evidence().sourceKey());
    }

    private SelectedCandidate toSelected(
            EvaluatedCandidate candidate,
            PreferenceEvidence preferences,
            RecommendationType type,
            int rank) {
        boolean cuisineLiked = preferences.likedCuisineCodes().contains(candidate.evidence().cuisineCode());
        List<ReasonCode> reasonCodes = reasonRenderer.reasonCodes(type, candidate.evidence(), cuisineLiked);
        BigDecimal score = score(candidate.evidence(), preferences, type).min(MAX_SCORE).setScale(7, RoundingMode.HALF_UP);
        return new SelectedCandidate(
                candidate,
                type,
                rank,
                score,
                reasonCodes,
                reasonRenderer.explanation(type, candidate.evidence(), reasonCodes));
    }

    private BigDecimal score(CandidateEvidence candidate, PreferenceEvidence preferences, RecommendationType type) {
        BigDecimal score = new BigDecimal("0.1000000");
        if (candidate.wantToTry()) {
            score = score.add(new BigDecimal("0.2500000"));
        }
        if (preferences.likedCuisineCodes().contains(candidate.cuisineCode())) {
            score = score.add(new BigDecimal("0.1800000"));
        }
        if (preferences.preferredMealTypes().contains(candidate.mealType())) {
            score = score.add(new BigDecimal("0.0700000"));
        }
        score = score.add(new BigDecimal(candidate.personalRecordCount()).multiply(new BigDecimal("0.0300000")));
        score = score.add(new BigDecimal(candidate.groupRecordCount()).multiply(new BigDecimal("0.0350000")));
        if (candidate.personalAverageRating() != null) {
            score = score.add(candidate.personalAverageRating().divide(new BigDecimal("25.0"), 7, RoundingMode.HALF_UP));
        }
        if (candidate.groupAverageRating() != null) {
            score = score.add(candidate.groupAverageRating().divide(new BigDecimal("20.0"), 7, RoundingMode.HALF_UP));
        }
        if (candidate.cleanliness() != null) {
            score = score.add(candidate.cleanliness().score().divide(new BigDecimal("20.0"), 7, RoundingMode.HALF_UP));
        }
        if (type == RecommendationType.EXPLORATORY
                && !preferences.likedCuisineCodes().contains(candidate.cuisineCode())
                && candidate.personalRecordCount() == 0) {
            score = score.add(new BigDecimal("0.2000000"));
        }
        if (type == RecommendationType.GROUP_INSPIRED && candidate.groupRecordCount() > 0) {
            score = score.add(new BigDecimal("0.3000000"));
        }
        return score;
    }

    private BigDecimal budgetFit(CandidateEvidence candidate, PreferenceEvidence preferences) {
        if (candidate.price() == null || preferences.budgetMax() == null || !candidate.price().currency().equals(preferences.currency())) {
            return BigDecimal.ZERO;
        }
        return preferences.budgetMax().subtract(candidate.price().amount()).max(BigDecimal.ZERO);
    }

    private BigDecimal distanceFit(CandidateEvidence candidate) {
        return candidate.distanceKm() == null ? new BigDecimal("999999") : candidate.distanceKm();
    }
}
