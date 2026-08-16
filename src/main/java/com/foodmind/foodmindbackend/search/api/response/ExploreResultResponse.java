package com.foodmind.foodmindbackend.search.api.response;

import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record ExploreResultResponse(
        String sourceType,
        UUID sourceId,
        String title,
        String subtitle,
        String snippet,
        UUID mediaAssetId,
        String imageReference,
        String visibility,
        UUID ownerUserId,
        UUID groupId,
        OffsetDateTime occurredAt) {

    public static ExploreResultResponse from(SearchDocument document) {
        return new ExploreResultResponse(
                exploreSourceType(document),
                document.sourceId(),
                document.title(),
                document.subtitle(),
                document.snippet(),
                document.mediaAssetId(),
                document.imageReference(),
                document.visibility(),
                document.ownerUserId(),
                document.groupId(),
                document.occurredAt());
    }

    private static String exploreSourceType(SearchDocument document) {
        if (document.sourceType() == SearchSourceType.FOOD_RECORD) {
            return "GROUP_RECORD";
        }
        if (document.sourceType() == SearchSourceType.FOOD_PRODUCT) {
            return "CURATED_PRODUCT";
        }
        return "CURATED_PLACE";
    }
}
