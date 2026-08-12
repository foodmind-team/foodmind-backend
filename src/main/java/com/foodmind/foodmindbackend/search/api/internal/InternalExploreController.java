package com.foodmind.foodmindbackend.search.api.internal;

import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import com.foodmind.foodmindbackend.common.security.InternalToolAuthorizer;
import com.foodmind.foodmindbackend.search.api.internal.request.InternalExploreRequest;
import com.foodmind.foodmindbackend.search.api.internal.response.InternalExploreResponse;
import com.foodmind.foodmindbackend.search.application.ExploreContent;
import com.foodmind.foodmindbackend.search.domain.ExploreCursor;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only catalogue and authorised-record exploration for the Chatbot. */
@RestController
@RequestMapping("/internal/v1/explore")
public class InternalExploreController {

    private final ExploreContent exploreContent;
    private final InternalToolAuthorizer internalToolAuthorizer;

    public InternalExploreController(ExploreContent exploreContent, InternalToolAuthorizer internalToolAuthorizer) {
        this.exploreContent = exploreContent;
        this.internalToolAuthorizer = internalToolAuthorizer;
    }

    @PostMapping
    public InternalExploreResponse explore(
            @RequestHeader("X-FoodMind-Delegation") String delegation,
            @RequestBody(required = false) InternalExploreRequest request) {
        DelegationTokenIssuer.VerifiedDelegationToken claims =
                internalToolAuthorizer.requireScope(delegation, DelegationTokenIssuer.SCOPE_CHAT_SEARCH);
        InternalExploreRequest safeRequest = request == null ? new InternalExploreRequest(null, null, null) : request;
        Set<SearchSourceType> sourceTypes = safeRequest.sourceTypes() == null || safeRequest.sourceTypes().isEmpty()
                ? EnumSet.allOf(SearchSourceType.class)
                : safeRequest.sourceTypes();
        int size = safeRequest.size() == null ? 10 : Math.min(safeRequest.size(), 10);
        return InternalExploreResponse.from(exploreContent.explore(
                claims.userId(), sourceTypes, size, ExploreCursor.after(safeRequest.after())));
    }
}
