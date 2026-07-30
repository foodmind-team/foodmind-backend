package com.foodmind.foodmindbackend.recommendation.domain;

import com.foodmind.foodmindbackend.recommendation.domain.filter.FilterCode;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 06:54 am
 */

public record EvaluatedCandidate(
        CandidateEvidence evidence,
        FilterCode filterCode) {

    public boolean eligible() {
        return filterCode == null;
    }
}
