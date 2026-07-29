package com.foodmind.foodmindbackend.catalog.application;

import com.foodmind.foodmindbackend.catalog.application.port.CatalogueDetailQuery;
import com.foodmind.foodmindbackend.catalog.application.port.CatalogueReferenceDataQuery;
import com.foodmind.foodmindbackend.catalog.domain.CatalogueReferenceData;
import com.foodmind.foodmindbackend.catalog.domain.MealDetail;
import com.foodmind.foodmindbackend.catalog.domain.PlaceDetail;
import com.foodmind.foodmindbackend.catalog.domain.ProductDetail;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

@Service
@Transactional(readOnly = true)
public class CatalogueService {

    private final CatalogueReferenceDataQuery referenceDataQuery;
    private final CatalogueDetailQuery detailQuery;

    public CatalogueService(CatalogueReferenceDataQuery referenceDataQuery, CatalogueDetailQuery detailQuery) {
        this.referenceDataQuery = referenceDataQuery;
        this.detailQuery = detailQuery;
    }

    public CatalogueReferenceData referenceData() {
        return referenceDataQuery.referenceData();
    }

    public MealDetail meal(UUID id) {
        return detailQuery.findActiveMeal(id).orElseThrow(this::notFound);
    }

    public PlaceDetail place(UUID id) {
        return detailQuery.findActivePlace(id).orElseThrow(this::notFound);
    }

    public ProductDetail product(UUID id) {
        return detailQuery.findActiveProduct(id).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }
}
