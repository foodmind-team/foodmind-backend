package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.port.DrinkRecordQuery;
import com.foodmind.foodmindbackend.record.domain.DrinkRecordFilter;
import com.foodmind.foodmindbackend.record.domain.DrinkRecordPage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

@Service
public class ListDrinkRecords {

    private final DrinkRecordQuery drinkRecordQuery;

    public ListDrinkRecords(DrinkRecordQuery drinkRecordQuery) {
        this.drinkRecordQuery = drinkRecordQuery;
    }

    @Transactional(readOnly = true)
    public DrinkRecordPage handle(UUID actorUserId, DrinkRecordFilter filter) {
        validate(filter);
        return drinkRecordQuery.listAuthorised(actorUserId, filter);
    }

    private void validate(DrinkRecordFilter filter) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            errors.add(new ApiFieldError("from", "RANGE_ORDER", "from must be before or equal to to."));
        }
        if (filter.minRating() != null && filter.maxRating() != null && filter.minRating().compareTo(filter.maxRating()) > 0) {
            errors.add(new ApiFieldError("minRating", "RANGE_ORDER", "minRating must be less than or equal to maxRating."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
        }
    }
}
