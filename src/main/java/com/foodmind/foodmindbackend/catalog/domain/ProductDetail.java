package com.foodmind.foodmindbackend.catalog.domain;

import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record ProductDetail(
        UUID id,
        String name,
        String brand,
        String description,
        Money price,
        PlaceSummary place,
        List<String> dietaryTagCodes,
        List<String> allergenCodes) {

    public ProductDetail {
        dietaryTagCodes = List.copyOf(dietaryTagCodes);
        allergenCodes = List.copyOf(allergenCodes);
    }
}
