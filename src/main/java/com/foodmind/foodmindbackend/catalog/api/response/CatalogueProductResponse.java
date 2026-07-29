package com.foodmind.foodmindbackend.catalog.api.response;

import com.foodmind.foodmindbackend.catalog.domain.Money;
import com.foodmind.foodmindbackend.catalog.domain.PlaceSummary;
import com.foodmind.foodmindbackend.catalog.domain.ProductDetail;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public record CatalogueProductResponse(
        UUID id,
        String name,
        String brand,
        String description,
        MoneyResponse price,
        PlaceSummaryResponse place,
        List<String> dietaryTagCodes,
        List<String> allergenCodes) {

    public static CatalogueProductResponse from(ProductDetail product) {
        return new CatalogueProductResponse(
                product.id(),
                product.name(),
                product.brand(),
                product.description(),
                MoneyResponse.from(product.price()),
                PlaceSummaryResponse.from(product.place()),
                product.dietaryTagCodes(),
                product.allergenCodes());
    }

    public record MoneyResponse(BigDecimal amount, String currency) {

        static MoneyResponse from(Money money) {
            return money == null ? null : new MoneyResponse(money.amount(), money.currency());
        }
    }

    public record PlaceSummaryResponse(UUID id, String name, String area, String placeType) {

        static PlaceSummaryResponse from(PlaceSummary place) {
            return place == null ? null : new PlaceSummaryResponse(place.id(), place.name(), place.area(), place.placeType());
        }
    }
}
