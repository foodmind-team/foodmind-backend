package com.foodmind.foodmindbackend.catalog.api.response;

import com.foodmind.foodmindbackend.catalog.domain.GeoPoint;
import com.foodmind.foodmindbackend.catalog.domain.Money;
import com.foodmind.foodmindbackend.catalog.domain.PlaceDetail;
import com.foodmind.foodmindbackend.catalog.domain.PlaceObservation;
import com.foodmind.foodmindbackend.catalog.domain.PlaceOffering;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record CataloguePlaceResponse(
        UUID id,
        String name,
        String placeType,
        String area,
        String addressText,
        CoordinatesResponse coordinates,
        Integer priceBand,
        List<ObservationResponse> observations,
        List<OfferingResponse> offerings) {

    public static CataloguePlaceResponse from(PlaceDetail place) {
        return new CataloguePlaceResponse(
                place.id(),
                place.name(),
                place.placeType(),
                place.area(),
                place.addressText(),
                CoordinatesResponse.from(place.coordinates()),
                place.priceBand(),
                place.observations().stream().map(ObservationResponse::from).toList(),
                place.offerings().stream().map(OfferingResponse::from).toList());
    }

    public record CoordinatesResponse(BigDecimal latitude, BigDecimal longitude) {

        static CoordinatesResponse from(GeoPoint coordinates) {
            return coordinates == null ? null : new CoordinatesResponse(coordinates.latitude(), coordinates.longitude());
        }
    }

    public record ObservationResponse(
            UUID id,
            String observationType,
            BigDecimal score,
            String note,
            String sourceKind,
            OffsetDateTime observedAt) {

        static ObservationResponse from(PlaceObservation observation) {
            return new ObservationResponse(
                    observation.id(),
                    observation.observationType(),
                    observation.score(),
                    observation.note(),
                    observation.sourceKind(),
                    observation.observedAt());
        }
    }

    public record OfferingResponse(
            UUID id,
            String displayName,
            MoneyResponse price,
            Integer spiceLevel,
            String availabilityNote,
            UUID mealId,
            String mealName,
            String mealType,
            String cuisineCode) {

        static OfferingResponse from(PlaceOffering offering) {
            return new OfferingResponse(
                    offering.id(),
                    offering.displayName(),
                    MoneyResponse.from(offering.price()),
                    offering.spiceLevel(),
                    offering.availabilityNote(),
                    offering.mealId(),
                    offering.mealName(),
                    offering.mealType(),
                    offering.cuisineCode());
        }
    }

    public record MoneyResponse(BigDecimal amount, String currency) {

        static MoneyResponse from(Money money) {
            return new MoneyResponse(money.amount(), money.currency());
        }
    }
}
