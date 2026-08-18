package com.foodmind.foodmindbackend.search.api.response;

import com.foodmind.foodmindbackend.catalog.api.CuratedCatalogueImage;
import com.foodmind.foodmindbackend.search.domain.SearchPage;
import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import java.util.List;
import java.util.function.Function;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record SearchPageResponse(List<SearchResultResponse> items, String nextCursor, boolean hasNext) {

    public static SearchPageResponse from(SearchPage page, Function<String, String> imageResolver) {
        return new SearchPageResponse(
                page.items().stream().map(item -> SearchResultResponse.from(item, imageReference(item, imageResolver))).toList(),
                page.nextCursor(),
                page.hasNext());
    }

    private static String imageReference(SearchDocument item, Function<String, String> imageResolver) {
        if (item.imageObjectKey() != null && !item.imageObjectKey().isBlank()) {
            return imageResolver.apply(item.imageObjectKey());
        }
        return CuratedCatalogueImage.referenceFor(item.sourceId());
    }
}
