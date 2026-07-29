package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
import com.foodmind.foodmindbackend.record.domain.FoodRecord;
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
public class GetFoodRecord {

    private final FoodRecordQuery foodRecordQuery;

    public GetFoodRecord(FoodRecordQuery foodRecordQuery) {
        this.foodRecordQuery = foodRecordQuery;
    }

    @Transactional(readOnly = true)
    public FoodRecord get(UUID actorUserId, UUID id) {
        return foodRecordQuery.findAuthorised(actorUserId, id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
