package com.foodmind.foodmindbackend.recommendation.domain;

import java.math.BigDecimal;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record MoneyAmount(BigDecimal amount, String currency) {
}
