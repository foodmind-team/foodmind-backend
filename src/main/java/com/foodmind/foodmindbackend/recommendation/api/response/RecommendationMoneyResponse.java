package com.foodmind.foodmindbackend.recommendation.api.response;

import com.foodmind.foodmindbackend.recommendation.domain.MoneyAmount;
import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record RecommendationMoneyResponse(BigDecimal amount, String currency) {

    public static RecommendationMoneyResponse from(MoneyAmount money) {
        return money == null ? null : new RecommendationMoneyResponse(money.amount(), money.currency());
    }
}
