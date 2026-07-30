package com.foodmind.foodmindbackend.search.api.internal.request;

import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record InternalSearchRequest(
        @NotBlank
        @Size(max = 200)
        String query,
        Set<SearchSourceType> sourceTypes,
        String after,
        Integer size) {
}
