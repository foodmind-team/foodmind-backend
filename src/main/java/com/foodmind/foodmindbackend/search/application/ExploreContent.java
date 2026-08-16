package com.foodmind.foodmindbackend.search.application;

import com.foodmind.foodmindbackend.search.application.port.AuthorisedExploreQuery;
import com.foodmind.foodmindbackend.search.application.port.ReadyMediaQuery;
import com.foodmind.foodmindbackend.search.domain.ExploreCursor;
import com.foodmind.foodmindbackend.search.domain.ExplorePage;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import java.util.List;
import java.util.stream.Collectors;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@Service
public class ExploreContent {

    public static final int MAX_PAGE_SIZE = 100;

    private final AuthorisedExploreQuery authorisedExploreQuery;
    private final ReadyMediaQuery readyMediaQuery;

    public ExploreContent(AuthorisedExploreQuery authorisedExploreQuery, ReadyMediaQuery readyMediaQuery) {
        this.authorisedExploreQuery = authorisedExploreQuery;
        this.readyMediaQuery = readyMediaQuery;
    }

    @Transactional(readOnly = true)
    public ExplorePage explore(UUID actorUserId, Set<SearchSourceType> sourceTypes, int size, ExploreCursor after) {
        Set<SearchSourceType> safeTypes = sourceTypes == null || sourceTypes.isEmpty()
                ? EnumSet.allOf(SearchSourceType.class)
                : EnumSet.copyOf(sourceTypes);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        ExplorePage page = authorisedExploreQuery.explore(actorUserId, safeTypes, safeSize, after);
        return new ExplorePage(withReadyMedia(page.items()), page.nextCursor());
    }

    private List<SearchDocument> withReadyMedia(List<SearchDocument> documents) {
        Set<UUID> recordIds = documents.stream()
                .filter(document -> document.sourceType() == SearchSourceType.FOOD_RECORD)
                .map(SearchDocument::sourceId)
                .collect(Collectors.toSet());
        var mediaByRecordId = readyMediaQuery.findReadyFoodMedia(recordIds);
        return documents.stream()
                .map(document -> document.withMediaAssetId(mediaByRecordId.get(document.sourceId())))
                .toList();
    }
}
