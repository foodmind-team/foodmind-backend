package com.foodmind.foodmindbackend.wanttotry.application.port;

import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryItem;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryPage;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySource;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySourceSummary;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public interface WantToTryRepository {

    Optional<WantToTrySourceSummary> resolveSource(UUID actorUserId, WantToTrySource source);

    WantToTryItem insertOrResolveDuplicate(UUID ownerUserId, WantToTrySource source, String note);

    WantToTryPage findOwnerPage(UUID ownerUserId, int page, int size);

    boolean softDeleteOwned(UUID ownerUserId, UUID id);
}
