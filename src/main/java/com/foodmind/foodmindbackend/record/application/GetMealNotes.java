package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
import com.foodmind.foodmindbackend.record.domain.MealNoteView;
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
public class GetMealNotes {

    private final FoodRecordQuery foodRecordQuery;

    public GetMealNotes(FoodRecordQuery foodRecordQuery) {
        this.foodRecordQuery = foodRecordQuery;
    }

    @Transactional(readOnly = true)
    public List<MealNoteView> forUser(UUID actorUserId, int limit) {
        return foodRecordQuery.mealNotesForUser(actorUserId, Math.max(1, Math.min(limit, 100)));
    }
}
