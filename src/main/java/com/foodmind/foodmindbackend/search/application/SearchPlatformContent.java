package com.foodmind.foodmindbackend.search.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.search.application.port.AuthorisedSearchQuery;
import com.foodmind.foodmindbackend.search.domain.SearchCursor;
import com.foodmind.foodmindbackend.search.domain.SearchPage;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
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
public class SearchPlatformContent {

    public static final int MAX_QUERY_LENGTH = 200;
    public static final int MAX_PAGE_SIZE = 100;

    private final AuthorisedSearchQuery authorisedSearchQuery;

    public SearchPlatformContent(AuthorisedSearchQuery authorisedSearchQuery) {
        this.authorisedSearchQuery = authorisedSearchQuery;
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
        return authorisedSearchQuery.search(actorUserId, trimmed, safeTypes, safeSize, after);
    }
}
