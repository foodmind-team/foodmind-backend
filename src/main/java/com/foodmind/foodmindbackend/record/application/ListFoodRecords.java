package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
import com.foodmind.foodmindbackend.record.domain.FoodRecordFilter;
import com.foodmind.foodmindbackend.record.domain.FoodRecordPage;
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
        return foodRecordQuery.listAuthorised(actorUserId, filter);
    }
}
