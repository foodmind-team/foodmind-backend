package com.foodmind.foodmindbackend.search.api.internal.response;

import com.foodmind.foodmindbackend.search.domain.SearchPage;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record InternalSearchResponse(List<InternalSearchDocumentResponse> items, String nextCursor, boolean hasNext) {

    public static InternalSearchResponse from(SearchPage page) {
        return new InternalSearchResponse(
                page.items().stream().map(InternalSearchDocumentResponse::from).toList(),
                page.nextCursor(),
                page.hasNext());
    }
}
