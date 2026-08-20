package com.foodmind.foodmindbackend.common.api;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext) {

    public static final int MAX_PAGE_SIZE = 100;

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("Page index must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("Total items must be zero or greater.");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("Total pages must be zero or greater.");
        }
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        boolean hasNext = page < totalPages - 1;
        return new PageResponse<>(items, page, size, totalItems, totalPages, hasNext);
    }
}
