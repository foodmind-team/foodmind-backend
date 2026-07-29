package com.foodmind.foodmindbackend.search.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record SearchPage(List<SearchDocument> items, String nextCursor) {

    public SearchPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean hasNext() {
        return nextCursor != null;
    }
}
