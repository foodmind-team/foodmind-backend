package com.foodmind.foodmindbackend.search.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.search.application.port.AuthorisedSearchQuery;
import com.foodmind.foodmindbackend.search.application.port.ReadyMediaQuery;
import com.foodmind.foodmindbackend.search.domain.SearchCursor;
import com.foodmind.foodmindbackend.search.domain.SearchPage;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@Service
public class SearchPlatformContent {

    public static final int MAX_QUERY_LENGTH = 200;
    public static final int MAX_PAGE_SIZE = 100;

    private final AuthorisedSearchQuery authorisedSearchQuery;
    private final ReadyMediaQuery readyMediaQuery;

    public SearchPlatformContent(AuthorisedSearchQuery authorisedSearchQuery, ReadyMediaQuery readyMediaQuery) {
        this.authorisedSearchQuery = authorisedSearchQuery;
        this.readyMediaQuery = readyMediaQuery;
    }

    @Transactional(readOnly = true)
    public SearchPage search(UUID actorUserId, String query, Set<SearchSourceType> sourceTypes, int size, SearchCursor after) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Search query must not be blank.");
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Search query must be 200 characters or fewer.");
        }
        Set<SearchSourceType> safeTypes = sourceTypes == null || sourceTypes.isEmpty()
                ? EnumSet.allOf(SearchSourceType.class)
                : EnumSet.copyOf(sourceTypes);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        SearchPage page = authorisedSearchQuery.search(actorUserId, trimmed, safeTypes, safeSize, after);
        return new SearchPage(withReadyMedia(page.items()), page.nextCursor());
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
