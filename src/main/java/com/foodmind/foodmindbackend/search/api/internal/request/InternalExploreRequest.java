package com.foodmind.foodmindbackend.search.api.internal.request;

import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import java.util.Set;

/** Delegation-scoped request for bounded, read-only platform exploration. */
public record InternalExploreRequest(Set<SearchSourceType> sourceTypes, String after, Integer size) {
}
