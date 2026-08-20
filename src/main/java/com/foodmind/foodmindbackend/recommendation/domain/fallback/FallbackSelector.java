package com.foodmind.foodmindbackend.recommendation.domain.fallback;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.CandidateSourceType;
import com.foodmind.foodmindbackend.recommendation.domain.EvaluatedCandidate;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationType;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import com.foodmind.foodmindbackend.recommendation.domain.filter.FilterCode;
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
        return select(evaluatedCandidates, null, preferences);
    }

    public List<SelectedCandidate> select(
            List<EvaluatedCandidate> evaluatedCandidates,
            RecommendationRequestContext request,
            PreferenceEvidence preferences) {
        List<EvaluatedCandidate> eligible = evaluatedCandidates.stream()
                .filter(EvaluatedCandidate::eligible)
                .toList();
        if (eligible.isEmpty()) {
            eligible = evaluatedCandidates.stream()
                    .filter(candidate -> canRelax(candidate.filterCode()))
                    .map(candidate -> new EvaluatedCandidate(candidate.evidence(), null))
                    .toList();
        }
        List<SelectedCandidate> selected = new ArrayList<>();
        Set<String> usedOfferings = new HashSet<>();

        // A saved record is an explicit product promise: reserve one returned card whenever one
        // survived the same hard filters as catalogue candidates.
        eligible.stream()
                .filter(candidate -> candidate.evidence().sourceType() == CandidateSourceType.FOOD_RECORD)
                .sorted(comparatorFor(RecommendationType.PERSONAL, request, preferences))
                .findFirst()
                .ifPresent(candidate -> {
                    RecommendationType type = candidate.evidence().groupRecordCount() > 0
                            ? RecommendationType.GROUP_INSPIRED : RecommendationType.PERSONAL;
                    selected.add(toSelected(candidate, request, preferences, type, 1));
                });
        selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));

        pick(eligible, request, preferences, RecommendationType.PERSONAL, usedOfferings)
                .ifPresent(candidate -> selected.add(toSelected(candidate, request, preferences, RecommendationType.PERSONAL, selected.size() + 1)));
        selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));

        pick(eligible, request, preferences, RecommendationType.EXPLORATORY, usedOfferings)
                .ifPresent(candidate -> selected.add(toSelected(candidate, request, preferences, RecommendationType.EXPLORATORY, selected.size() + 1)));
        selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));

        pick(eligible, request, preferences, RecommendationType.GROUP_INSPIRED, usedOfferings)
                .ifPresent(candidate -> selected.add(toSelected(candidate, request, preferences, RecommendationType.GROUP_INSPIRED, selected.size() + 1)));

        if (selected.size() < 3) {
            usedOfferings.clear();
            selected.forEach(candidate -> usedOfferings.add(candidate.candidate().evidence().sourceKey()));
            eligible.stream()
                    .filter(candidate -> !usedOfferings.contains(candidate.evidence().sourceKey()))
                    .sorted(comparatorFor(RecommendationType.PERSONAL, request, preferences))
                    .limit(3 - selected.size())
                    .forEach(candidate -> selected.add(toSelected(candidate, request, preferences, RecommendationType.PERSONAL, selected.size() + 1)));
        }
        return selected;
    }

    private java.util.Optional<EvaluatedCandidate> pick(
            List<EvaluatedCandidate> eligible,
            RecommendationRequestContext request,
            PreferenceEvidence preferences,
            RecommendationType type,
            Set<String> usedOfferings) {
        boolean hasGroupEvidence = eligible.stream().anyMatch(candidate -> candidate.evidence().groupRecordCount() > 0);
        return eligible.stream()
                .filter(candidate -> !usedOfferings.contains(candidate.evidence().sourceKey()))
                .filter(candidate -> matchesType(candidate, preferences, type, hasGroupEvidence))
                .sorted(comparatorFor(type, request, preferences))
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

    private Comparator<EvaluatedCandidate> comparatorFor(
            RecommendationType type,
            RecommendationRequestContext request,
            PreferenceEvidence preferences) {
        return Comparator
                .comparing((EvaluatedCandidate candidate) -> candidate.evidence().wantToTry()).reversed()
                .thenComparing(candidate -> score(candidate.evidence(), request, preferences, type), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.evidence().sourceKey());
    }

    private SelectedCandidate toSelected(
            EvaluatedCandidate candidate,
            RecommendationRequestContext request,
            PreferenceEvidence preferences,
            RecommendationType type,
            int rank) {
        boolean cuisineLiked = preferences.likedCuisineCodes().contains(candidate.evidence().cuisineCode());
        List<ReasonCode> reasonCodes = reasonRenderer.reasonCodes(type, candidate.evidence(), cuisineLiked);
        BigDecimal score = score(candidate.evidence(), request, preferences, type).min(MAX_SCORE).setScale(7, RoundingMode.HALF_UP);
        return new SelectedCandidate(
                candidate,
                type,
                rank,
                score,
                reasonCodes,
                reasonRenderer.explanation(type, candidate.evidence(), reasonCodes));
    }

    private BigDecimal score(
            CandidateEvidence candidate,
            RecommendationRequestContext request,
            PreferenceEvidence preferences,
            RecommendationType type) {
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
        score = score.add(budgetFit(candidate, request, preferences));
        score = score.add(distanceFit(candidate, request, preferences));
        score = score.add(spiceFit(candidate, request, preferences));
        score = score.add(cleanlinessFit(candidate, request, preferences));
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

    private boolean canRelax(FilterCode filterCode) {
        return filterCode == FilterCode.DISLIKED_CUISINE || filterCode == FilterCode.RECENT_REPEAT;
    }

    private BigDecimal budgetFit(
            CandidateEvidence candidate,
            RecommendationRequestContext request,
            PreferenceEvidence preferences) {
        BigDecimal budget = request != null && request.maxBudget() != null ? request.maxBudget() : preferences.budgetMax();
        String currency = request != null && request.currency() != null ? request.currency() : preferences.currency();
        if (candidate.price() == null || budget == null || budget.signum() <= 0 || currency == null
                || !candidate.price().currency().equals(currency) || candidate.price().amount().compareTo(budget) > 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal remainingRatio = budget.subtract(candidate.price().amount())
                .max(BigDecimal.ZERO)
                .divide(budget, 7, RoundingMode.HALF_UP);
        return new BigDecimal("0.0400000").add(remainingRatio.multiply(new BigDecimal("0.0800000")));
    }

    private BigDecimal distanceFit(
            CandidateEvidence candidate,
            RecommendationRequestContext request,
            PreferenceEvidence preferences) {
        if (candidate.distanceKm() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal maxDistance = request != null && request.maxDistanceKm() != null
                ? request.maxDistanceKm() : preferences.maxDistanceKm();
        if (maxDistance != null) {
            if (candidate.distanceKm().compareTo(maxDistance) > 0) {
                return BigDecimal.ZERO;
            }
            if (maxDistance.signum() == 0) {
                return new BigDecimal("0.1000000");
            }
            BigDecimal remainingRatio = maxDistance.subtract(candidate.distanceKm())
                    .max(BigDecimal.ZERO)
                    .divide(maxDistance, 7, RoundingMode.HALF_UP);
            return new BigDecimal("0.0200000").add(remainingRatio.multiply(new BigDecimal("0.0800000")));
        }
        return BigDecimal.ONE.divide(BigDecimal.ONE.add(candidate.distanceKm()), 7, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.0600000"));
    }

    private BigDecimal spiceFit(
            CandidateEvidence candidate,
            RecommendationRequestContext request,
            PreferenceEvidence preferences) {
        Integer maxSpice = request != null && request.maxSpiceLevel() != null
                ? request.maxSpiceLevel() : preferences.spiceTolerance();
        if (maxSpice == null || candidate.spiceLevel() == null || candidate.spiceLevel() > maxSpice) {
            return BigDecimal.ZERO;
        }
        if (maxSpice == 0) {
            return new BigDecimal("0.1000000");
        }
        return new BigDecimal("0.0600000").add(
                BigDecimal.valueOf(maxSpice - candidate.spiceLevel())
                        .max(BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(maxSpice), 7, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("0.0400000")));
    }

    private BigDecimal cleanlinessFit(
            CandidateEvidence candidate,
            RecommendationRequestContext request,
            PreferenceEvidence preferences) {
        BigDecimal threshold = request != null && request.minimumCleanlinessEvidenceScore() != null
                ? request.minimumCleanlinessEvidenceScore() : preferences.minimumCleanlinessEvidenceScore();
        if (threshold == null || candidate.cleanliness() == null || candidate.cleanliness().score().compareTo(threshold) < 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal("0.1000000");
    }
}
