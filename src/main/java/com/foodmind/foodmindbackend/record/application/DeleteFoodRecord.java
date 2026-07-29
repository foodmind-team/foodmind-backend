package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
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
public class DeleteFoodRecord {

    private final FoodRecordQuery foodRecordQuery;

    public DeleteFoodRecord(FoodRecordQuery foodRecordQuery) {
        this.foodRecordQuery = foodRecordQuery;
    }

    @Transactional
    public void delete(UUID ownerUserId, UUID id) {
        foodRecordQuery.softDelete(ownerUserId, id);
    }
}
