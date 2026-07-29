package com.foodmind.foodmindbackend.search.api.response;

import com.foodmind.foodmindbackend.search.domain.SearchPage;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record SearchPageResponse(List<SearchResultResponse> items, String nextCursor, boolean hasNext) {

    public static SearchPageResponse from(SearchPage page) {
        return new SearchPageResponse(
                page.items().stream().map(SearchResultResponse::from).toList(),
                page.nextCursor(),
                page.hasNext());
    }
}
