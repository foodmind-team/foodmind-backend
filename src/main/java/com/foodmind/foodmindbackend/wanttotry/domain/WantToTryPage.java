package com.foodmind.foodmindbackend.wanttotry.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record WantToTryPage(List<WantToTryItem> items, long totalItems) {

    public WantToTryPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
