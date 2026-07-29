package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.port.DrinkRecordQuery;
import com.foodmind.foodmindbackend.record.domain.DrinkRecord;
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
public class GetDrinkRecord {

    private final DrinkRecordQuery drinkRecordQuery;

    public GetDrinkRecord(DrinkRecordQuery drinkRecordQuery) {
        this.drinkRecordQuery = drinkRecordQuery;
    }

    @Transactional(readOnly = true)
    public DrinkRecord handle(UUID actorUserId, UUID id) {
        return drinkRecordQuery.findVisibleById(actorUserId, id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
