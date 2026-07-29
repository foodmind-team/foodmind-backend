package com.foodmind.foodmindbackend.group.api.response;

import com.foodmind.foodmindbackend.group.domain.FeedSourceType;
import com.foodmind.foodmindbackend.group.domain.GroupFeedEvent;
import com.foodmind.foodmindbackend.group.domain.GroupFeedPage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupFeedResponse(List<Item> items, String nextCursor) {

    public static GroupFeedResponse from(GroupFeedPage page) {
        return new GroupFeedResponse(page.items().stream().map(Item::from).toList(), page.nextCursor());
    }

    public record Item(
            FeedSourceType sourceType,
            UUID sourceId,
            OffsetDateTime occurredAt,
            UUID actorUserId,
            String actorDisplayName,
            UUID foodRecordId,
            String mealNameSnapshot,
            UUID recommendationShareId,
            UUID recommendationCandidateId,
            String message) {

        static Item from(GroupFeedEvent event) {
            return new Item(
                    event.sourceType(),
                    event.sourceId(),
                    event.occurredAt(),
                    event.actorUserId(),
                    event.actorDisplayName(),
                    event.foodRecordId(),
                    event.mealNameSnapshot(),
                    event.recommendationShareId(),
                    event.recommendationCandidateId(),
                    event.message());
        }
    }
}
