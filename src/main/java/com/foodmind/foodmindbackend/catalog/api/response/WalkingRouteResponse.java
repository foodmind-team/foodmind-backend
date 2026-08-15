package com.foodmind.foodmindbackend.catalog.api.response;

import com.foodmind.foodmindbackend.catalog.domain.GeoPoint;
import com.foodmind.foodmindbackend.catalog.domain.WalkingRoute;
import java.math.BigDecimal;
import java.util.List;

public record WalkingRouteResponse(CoordinatesResponse origin, CoordinatesResponse destination, long distanceMeters, long durationSeconds, List<List<BigDecimal>> coordinates) {
    public static WalkingRouteResponse from(WalkingRoute route) {
        return new WalkingRouteResponse(CoordinatesResponse.from(route.origin()), CoordinatesResponse.from(route.destination()), route.distanceMeters(), route.durationSeconds(), route.coordinates().stream().map(point -> List.of(point.longitude(), point.latitude())).toList());
    }
    public record CoordinatesResponse(BigDecimal latitude, BigDecimal longitude) {
        static CoordinatesResponse from(GeoPoint point) { return new CoordinatesResponse(point.latitude(), point.longitude()); }
    }
}
