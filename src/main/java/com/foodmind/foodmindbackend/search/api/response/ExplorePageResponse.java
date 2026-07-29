package com.foodmind.foodmindbackend.search.api.response;

import com.foodmind.foodmindbackend.search.domain.ExplorePage;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record ExplorePageResponse(List<ExploreResultResponse> items, String nextCursor, boolean hasNext) {

    public static ExplorePageResponse from(ExplorePage page) {
        return new ExplorePageResponse(
                page.items().stream().map(ExploreResultResponse::from).toList(),
                page.nextCursor(),
                page.hasNext());
    }
}
