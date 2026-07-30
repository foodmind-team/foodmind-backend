package com.foodmind.foodmindbackend.search.api.internal;

import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import com.foodmind.foodmindbackend.common.security.InternalToolAuthorizer;
import com.foodmind.foodmindbackend.search.api.internal.request.InternalSearchRequest;
import com.foodmind.foodmindbackend.search.api.internal.response.InternalSearchResponse;
import com.foodmind.foodmindbackend.search.application.SearchPlatformContent;
import com.foodmind.foodmindbackend.search.domain.SearchCursor;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@RestController
@RequestMapping("/internal/v1/search")
public class InternalSearchController {

    private final SearchPlatformContent searchPlatformContent;
    private final InternalToolAuthorizer internalToolAuthorizer;

    public InternalSearchController(SearchPlatformContent searchPlatformContent, InternalToolAuthorizer internalToolAuthorizer) {
        this.searchPlatformContent = searchPlatformContent;
        this.internalToolAuthorizer = internalToolAuthorizer;
    }

    @PostMapping
    public InternalSearchResponse search(
            @RequestHeader("X-FoodMind-Delegation") String delegation,
            @Valid @RequestBody InternalSearchRequest request) {
        DelegationTokenIssuer.VerifiedDelegationToken claims =
                internalToolAuthorizer.requireScope(delegation, DelegationTokenIssuer.SCOPE_CHAT_SEARCH);
        Set<SearchSourceType> sourceTypes = request.sourceTypes() == null || request.sourceTypes().isEmpty()
                ? EnumSet.allOf(SearchSourceType.class)
                : request.sourceTypes();
        int size = request.size() == null ? 10 : Math.min(request.size(), 20);
        return InternalSearchResponse.from(searchPlatformContent.search(
                claims.userId(),
                request.query(),
                sourceTypes,
                size,
                SearchCursor.after(request.after())));
    }
}
