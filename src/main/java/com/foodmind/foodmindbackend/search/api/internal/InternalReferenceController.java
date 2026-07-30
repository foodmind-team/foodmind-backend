package com.foodmind.foodmindbackend.search.api.internal;

import com.foodmind.foodmindbackend.chat.application.port.ChatReferenceQuery;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import com.foodmind.foodmindbackend.common.security.InternalToolAuthorizer;
import com.foodmind.foodmindbackend.search.api.internal.request.InternalResolveReferencesRequest;
import com.foodmind.foodmindbackend.search.api.internal.response.InternalReferenceResponse;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/internal/v1/references")
public class InternalReferenceController {

    private final ChatReferenceQuery referenceQuery;
    private final InternalToolAuthorizer internalToolAuthorizer;

    public InternalReferenceController(ChatReferenceQuery referenceQuery, InternalToolAuthorizer internalToolAuthorizer) {
        this.referenceQuery = referenceQuery;
        this.internalToolAuthorizer = internalToolAuthorizer;
    }

    @PostMapping("/resolve")
    public List<InternalReferenceResponse> resolve(
            @RequestHeader("X-FoodMind-Delegation") String delegation,
            @Valid @RequestBody InternalResolveReferencesRequest request) {
        if (request.sessionId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "sessionId is required.");
        }
        DelegationTokenIssuer.VerifiedDelegationToken claims =
                internalToolAuthorizer.requireScope(delegation, DelegationTokenIssuer.SCOPE_CHAT_REFERENCE_RESOLVE);
        List<java.util.UUID> allowed = claims.referenceIds();
        if (request.referenceIds() != null && !allowed.containsAll(request.referenceIds())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return referenceQuery.resolveSessionReferences(claims.userId(), request.sessionId(), request.referenceIds())
                .stream()
                .map(InternalReferenceResponse::from)
                .toList();
    }
}
