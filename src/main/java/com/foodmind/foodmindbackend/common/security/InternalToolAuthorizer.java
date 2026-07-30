package com.foodmind.foodmindbackend.common.security;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Component
public class InternalToolAuthorizer {

    private static final String DELEGATION_PREFIX = "Bearer ";

    private final DelegationTokenIssuer delegationTokenIssuer;

    public InternalToolAuthorizer(DelegationTokenIssuer delegationTokenIssuer) {
        this.delegationTokenIssuer = delegationTokenIssuer;
    }

    public DelegationTokenIssuer.VerifiedDelegationToken requireScope(String delegationHeader, String requiredScope) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "SERVICE".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Service identity is required.");
        }
        if (delegationHeader == null || !delegationHeader.startsWith(DELEGATION_PREFIX)) {
            throw new BadCredentialsException("Delegation token is required.");
        }
        DelegationTokenIssuer.VerifiedDelegationToken token =
                delegationTokenIssuer.verify(delegationHeader.substring(DELEGATION_PREFIX.length()));
        List<String> scopes = token.scopes();
        if (scopes == null || !scopes.contains(requiredScope)) {
            throw new AccessDeniedException("Delegation scope is not allowed.");
        }
        return token;
    }
}
