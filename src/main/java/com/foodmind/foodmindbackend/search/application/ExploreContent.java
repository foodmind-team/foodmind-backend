package com.foodmind.foodmindbackend.search.application;

import com.foodmind.foodmindbackend.search.application.port.AuthorisedExploreQuery;
import com.foodmind.foodmindbackend.search.domain.ExploreCursor;
import com.foodmind.foodmindbackend.search.domain.ExplorePage;
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
public class ExploreContent {

    public static final int MAX_PAGE_SIZE = 100;

    private final AuthorisedExploreQuery authorisedExploreQuery;

    public ExploreContent(AuthorisedExploreQuery authorisedExploreQuery) {
        this.authorisedExploreQuery = authorisedExploreQuery;
    }

    @Transactional(readOnly = true)
    public ExplorePage explore(UUID actorUserId, Set<SearchSourceType> sourceTypes, int size, ExploreCursor after) {
        Set<SearchSourceType> safeTypes = sourceTypes == null || sourceTypes.isEmpty()
                ? EnumSet.allOf(SearchSourceType.class)
                : EnumSet.copyOf(sourceTypes);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return authorisedExploreQuery.explore(actorUserId, safeTypes, safeSize, after);
    }
}
