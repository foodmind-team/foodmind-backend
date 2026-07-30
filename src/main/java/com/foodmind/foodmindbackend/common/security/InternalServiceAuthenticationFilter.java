package com.foodmind.foodmindbackend.common.security;

import com.foodmind.foodmindbackend.common.error.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Component
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecurityProperties properties;
    private final GlobalExceptionHandler exceptionHandler;

    public InternalServiceAuthenticationFilter(SecurityProperties properties, GlobalExceptionHandler exceptionHandler) {
        this.properties = properties;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String expected = properties.getInternalService().getToken();
        String authorization = request.getHeader("Authorization");
        if (expected == null || expected.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String supplied = authorization.substring(BEARER_PREFIX.length());
            if (!expected.equals(supplied)) {
                throw new BadCredentialsException("Invalid service token.");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "foodmind-agent-service",
                    null,
                    List.of(new SimpleGrantedAuthority("SERVICE")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (BadCredentialsException exception) {
            SecurityContextHolder.clearContext();
            exceptionHandler.handleAuthenticationFailure(request, response, exception);
        }
    }
}
