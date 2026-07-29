package com.foodmind.foodmindbackend.record.application.port;

import com.foodmind.foodmindbackend.record.domain.HistoryBucket;
import com.foodmind.foodmindbackend.record.domain.HistoryEntry;
import com.foodmind.foodmindbackend.record.domain.HistoryFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

public interface HistoryQuery {

    Optional<String> userTimeZone(UUID actorUserId);

    List<HistoryEntry> findAuthorisedHistory(UUID actorUserId, HistoryFilter filter);

    List<HistoryBucket> findAuthorisedBuckets(UUID actorUserId, HistoryFilter filter);
}
