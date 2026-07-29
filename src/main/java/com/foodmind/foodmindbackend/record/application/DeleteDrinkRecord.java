package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.record.application.port.DrinkRecordQuery;
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
public class DeleteDrinkRecord {

    private final DrinkRecordQuery drinkRecordQuery;

    public DeleteDrinkRecord(DrinkRecordQuery drinkRecordQuery) {
        this.drinkRecordQuery = drinkRecordQuery;
    }

    @Transactional
    public void handle(UUID ownerUserId, UUID id) {
        drinkRecordQuery.softDelete(ownerUserId, id);
    }
}
