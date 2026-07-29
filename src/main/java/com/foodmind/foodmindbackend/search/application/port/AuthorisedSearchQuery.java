package com.foodmind.foodmindbackend.search.application.port;

import com.foodmind.foodmindbackend.search.domain.SearchCursor;
import com.foodmind.foodmindbackend.search.domain.SearchPage;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import java.util.Set;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public interface AuthorisedSearchQuery {

    SearchPage search(UUID actorUserId, String query, Set<SearchSourceType> sourceTypes, int pageSize, SearchCursor after);
}
