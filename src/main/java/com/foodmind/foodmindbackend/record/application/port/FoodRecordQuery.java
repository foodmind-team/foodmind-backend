package com.foodmind.foodmindbackend.record.application.port;

import com.foodmind.foodmindbackend.record.domain.FoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordFilter;
import com.foodmind.foodmindbackend.record.domain.FoodRecordPage;
import com.foodmind.foodmindbackend.record.domain.MealNoteView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

public interface FoodRecordQuery {

    FoodRecord create(FoodRecord record);

    Optional<FoodRecord> findAuthorised(UUID actorUserId, UUID id);

    Optional<FoodRecord> findOwnerRecord(UUID ownerUserId, UUID id);

    FoodRecordPage listAuthorised(UUID actorUserId, FoodRecordFilter filter);

    FoodRecord update(FoodRecord record);

    boolean softDelete(UUID ownerUserId, UUID id);

    boolean mealExists(UUID mealId);

    boolean placeExists(UUID placeId);

    boolean cuisineExists(UUID cuisineId);

    boolean readyMediaExistsForOwner(UUID ownerUserId, UUID mediaAssetId);

    List<MealNoteView> mealNotesForUser(UUID actorUserId, int limit);
}
