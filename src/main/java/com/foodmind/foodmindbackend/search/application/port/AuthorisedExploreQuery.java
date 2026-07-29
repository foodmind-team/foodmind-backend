package com.foodmind.foodmindbackend.search.application.port;

import com.foodmind.foodmindbackend.search.domain.ExploreCursor;
import com.foodmind.foodmindbackend.search.domain.ExplorePage;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import java.util.Set;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public interface AuthorisedExploreQuery {

    ExplorePage explore(UUID actorUserId, Set<SearchSourceType> sourceTypes, int pageSize, ExploreCursor after);
}
