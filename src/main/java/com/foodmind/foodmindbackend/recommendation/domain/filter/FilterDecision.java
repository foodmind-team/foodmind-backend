package com.foodmind.foodmindbackend.recommendation.domain.filter;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record FilterDecision(boolean allowed, FilterCode code) {

    public static FilterDecision allow() {
        return new FilterDecision(true, null);
    }

    public static FilterDecision reject(FilterCode code) {
        return new FilterDecision(false, code);
    }
}
