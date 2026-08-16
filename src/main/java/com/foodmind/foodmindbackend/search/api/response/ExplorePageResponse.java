package com.foodmind.foodmindbackend.search.api.response;

import com.foodmind.foodmindbackend.search.domain.ExplorePage;
import java.util.List;
import java.util.function.Function;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record ExplorePageResponse(List<ExploreResultResponse> items, String nextCursor, boolean hasNext) {

    public static ExplorePageResponse from(ExplorePage page, Function<String, String> imageResolver) {
        return new ExplorePageResponse(
                page.items().stream().map(item -> ExploreResultResponse.from(item, imageResolver.apply(item.imageObjectKey()))).toList(),
                page.nextCursor(),
                page.hasNext());
    }
}
