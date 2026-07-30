package com.foodmind.foodmindbackend.recommendation.domain.filter;

import com.foodmind.foodmindbackend.recommendation.domain.CandidateEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import com.foodmind.foodmindbackend.recommendation.domain.PreferenceEvidence;
import com.foodmind.foodmindbackend.recommendation.domain.RecommendationRequestContext;
import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public class BudgetCurrencyFilterPolicy implements HardFilterPolicy {

    @Override
    public FilterDecision apply(RecommendationRequestContext request, PreferenceEvidence preferences, CandidateEvidence candidate) {
        BigDecimal maxBudget = request.maxBudget() == null ? preferences.budgetMax() : request.maxBudget();
        String currency = request.currency() == null ? preferences.currency() : request.currency();
        if (maxBudget == null) {
            return FilterDecision.allow();
        }
        MoneyAmount price = candidate.price();
        boolean withinBudget = price != null
                && price.currency().equals(currency)
                && price.amount().compareTo(maxBudget) <= 0;
        return withinBudget ? FilterDecision.allow() : FilterDecision.reject(FilterCode.BUDGET_CURRENCY);
    }
}
