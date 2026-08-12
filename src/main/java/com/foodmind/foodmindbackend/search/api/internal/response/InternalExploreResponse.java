package com.foodmind.foodmindbackend.search.api.internal.response;

import com.foodmind.foodmindbackend.search.domain.ExplorePage;
import java.util.List;

/** Bounded authorised exploration result used only by delegated agent tools. */
public record InternalExploreResponse(List<InternalSearchDocumentResponse> items, String nextCursor, boolean hasNext) {

    public static InternalExploreResponse from(ExplorePage page) {
        return new InternalExploreResponse(
                page.items().stream().map(InternalSearchDocumentResponse::from).toList(),
                page.nextCursor(),
                page.hasNext());
    }
}
