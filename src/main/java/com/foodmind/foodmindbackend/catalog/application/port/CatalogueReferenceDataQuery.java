package com.foodmind.foodmindbackend.catalog.application.port;

import com.foodmind.foodmindbackend.catalog.domain.CatalogueReferenceData;
import com.foodmind.foodmindbackend.catalog.domain.ReferenceItem;
import java.util.Optional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public interface CatalogueReferenceDataQuery {

    CatalogueReferenceData referenceData();

    Optional<ReferenceItem> findCuisineByCode(String code);

    Optional<ReferenceItem> findDietaryTagByCode(String code);

    Optional<ReferenceItem> findAllergenByCode(String code);
}
