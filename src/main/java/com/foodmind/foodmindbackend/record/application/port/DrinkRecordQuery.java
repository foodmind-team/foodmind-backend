package com.foodmind.foodmindbackend.record.application.port;

import com.foodmind.foodmindbackend.record.domain.DrinkRecord;
import com.foodmind.foodmindbackend.record.domain.DrinkRecordFilter;
import com.foodmind.foodmindbackend.record.domain.DrinkRecordPage;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public interface DrinkRecordQuery {

    DrinkRecord create(DrinkRecord record);

    Optional<DrinkRecord> findVisibleById(UUID actorUserId, UUID id);

    Optional<DrinkRecord> findOwnerRecord(UUID ownerUserId, UUID id);

    DrinkRecordPage listAuthorised(UUID actorUserId, DrinkRecordFilter filter);

    DrinkRecord update(DrinkRecord record);

    boolean softDelete(UUID ownerUserId, UUID id);

    boolean placeExists(UUID placeId);

    boolean readyMediaExistsForOwner(UUID ownerUserId, UUID mediaAssetId);
}
