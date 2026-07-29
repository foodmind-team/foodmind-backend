package com.foodmind.foodmindbackend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(
        String field,
        String code,
        String message) {
}
