package com.foodmind.foodmindbackend.catalog.application.port;

import com.foodmind.foodmindbackend.catalog.domain.MealDetail;
import com.foodmind.foodmindbackend.catalog.domain.PlaceDetail;
import com.foodmind.foodmindbackend.catalog.domain.ProductDetail;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public interface CatalogueDetailQuery {

    Optional<MealDetail> findActiveMeal(UUID id);

    Optional<PlaceDetail> findActivePlace(UUID id);

    Optional<ProductDetail> findActiveProduct(UUID id);
}
