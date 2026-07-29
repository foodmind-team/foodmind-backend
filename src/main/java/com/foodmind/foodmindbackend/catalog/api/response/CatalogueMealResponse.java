package com.foodmind.foodmindbackend.catalog.api.response;

import com.foodmind.foodmindbackend.catalog.domain.MealDetail;
import com.foodmind.foodmindbackend.catalog.domain.MealOffering;
import com.foodmind.foodmindbackend.catalog.domain.Money;
import com.foodmind.foodmindbackend.catalog.domain.PlaceSummary;
import com.foodmind.foodmindbackend.catalog.domain.ReferenceItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record CatalogueMealResponse(
        UUID id,
        String name,
        String description,
        ReferenceItemResponse cuisine,
        String mealType,
        Integer defaultSpiceLevel,
        List<String> dietaryTagCodes,
        List<String> allergenCodes,
        List<OfferingResponse> offerings) {

    public static CatalogueMealResponse from(MealDetail meal) {
        return new CatalogueMealResponse(
                meal.id(),
                meal.name(),
                meal.description(),
                ReferenceItemResponse.from(meal.cuisine()),
                meal.mealType(),
                meal.defaultSpiceLevel(),
                meal.dietaryTagCodes(),
                meal.allergenCodes(),
                meal.offerings().stream().map(OfferingResponse::from).toList());
    }

    public record ReferenceItemResponse(UUID id, String code, String name) {

        static ReferenceItemResponse from(ReferenceItem item) {
            return new ReferenceItemResponse(item.id(), item.code(), item.name());
        }
    }

    public record OfferingResponse(
            UUID id,
            String displayName,
            MoneyResponse price,
            Integer spiceLevel,
            String availabilityNote,
            PlaceSummaryResponse place) {

        static OfferingResponse from(MealOffering offering) {
            return new OfferingResponse(
                    offering.id(),
                    offering.displayName(),
                    MoneyResponse.from(offering.price()),
                    offering.spiceLevel(),
                    offering.availabilityNote(),
                    PlaceSummaryResponse.from(offering.place()));
        }
    }

    public record MoneyResponse(BigDecimal amount, String currency) {

        static MoneyResponse from(Money money) {
            return new MoneyResponse(money.amount(), money.currency());
        }
    }

    public record PlaceSummaryResponse(UUID id, String name, String area, String placeType) {

        static PlaceSummaryResponse from(PlaceSummary place) {
            return new PlaceSummaryResponse(place.id(), place.name(), place.area(), place.placeType());
        }
    }
}
