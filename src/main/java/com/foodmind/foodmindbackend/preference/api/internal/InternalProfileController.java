package com.foodmind.foodmindbackend.preference.api.internal;

import com.foodmind.foodmindbackend.common.security.DelegationTokenIssuer;
import com.foodmind.foodmindbackend.common.security.InternalToolAuthorizer;
import com.foodmind.foodmindbackend.preference.api.internal.response.InternalProfileResponse;
import com.foodmind.foodmindbackend.preference.application.GetPreferences;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Delegation-scoped profile projection for read-only internal chat tools. */
@RestController
@RequestMapping("/internal/v1/profile")
public class InternalProfileController {

    private final GetPreferences getPreferences;
    private final InternalToolAuthorizer internalToolAuthorizer;

    public InternalProfileController(GetPreferences getPreferences, InternalToolAuthorizer internalToolAuthorizer) {
        this.getPreferences = getPreferences;
        this.internalToolAuthorizer = internalToolAuthorizer;
    }

    @GetMapping
    public InternalProfileResponse get(@RequestHeader("X-FoodMind-Delegation") String delegation) {
        DelegationTokenIssuer.VerifiedDelegationToken claims =
                internalToolAuthorizer.requireScope(delegation, DelegationTokenIssuer.SCOPE_CHAT_PROFILE);
        return InternalProfileResponse.from(getPreferences.get(claims.userId()));
    }
}
