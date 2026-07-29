package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
import com.foodmind.foodmindbackend.record.domain.FoodRecordFilter;
import com.foodmind.foodmindbackend.record.domain.FoodRecordPage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

@Service
public class ListFoodRecords {

    private final FoodRecordQuery foodRecordQuery;

    public ListFoodRecords(FoodRecordQuery foodRecordQuery) {
        this.foodRecordQuery = foodRecordQuery;
    }

    @Transactional(readOnly = true)
    public FoodRecordPage list(UUID actorUserId, FoodRecordFilter filter) {
        validate(filter);
        return foodRecordQuery.listAuthorised(actorUserId, filter);
    }

    private void validate(FoodRecordFilter filter) {
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
