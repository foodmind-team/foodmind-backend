package com.foodmind.foodmindbackend.catalog.domain;

import java.util.List;

/** A transient walking route. Neither endpoint is persisted. */
public record WalkingRoute(GeoPoint origin, GeoPoint destination, long distanceMeters, long durationSeconds, List<GeoPoint> coordinates) {
    public WalkingRoute {
        coordinates = List.copyOf(coordinates);
    }
}
