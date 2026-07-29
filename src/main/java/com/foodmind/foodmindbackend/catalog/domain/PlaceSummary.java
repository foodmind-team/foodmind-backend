package com.foodmind.foodmindbackend.catalog.domain;

import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record PlaceSummary(UUID id, String name, String area, String placeType) {
}
