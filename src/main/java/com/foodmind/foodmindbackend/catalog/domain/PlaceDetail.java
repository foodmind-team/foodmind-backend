package com.foodmind.foodmindbackend.catalog.domain;

import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record PlaceDetail(
        UUID id,
        String name,
        String placeType,
        String area,
        String addressText,
        GeoPoint coordinates,
        Integer priceBand,
        List<PlaceObservation> observations,
        List<PlaceOffering> offerings) {

    public PlaceDetail {
        observations = List.copyOf(observations);
        offerings = List.copyOf(offerings);
    }
}
