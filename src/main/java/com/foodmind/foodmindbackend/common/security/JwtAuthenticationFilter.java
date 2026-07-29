package com.foodmind.foodmindbackend.common.security;

import com.foodmind.foodmindbackend.common.error.GlobalExceptionHandler;
import com.foodmind.foodmindbackend.user.application.port.UserAccountRepository;
import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.domain.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.AuthenticationException;
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
 * @date: 29/7/2026 8:00 pm
 */

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtIssuer jwtIssuer;
    private final UserAccountRepository userAccountRepository;
    private final GlobalExceptionHandler exceptionHandler;

    public JwtAuthenticationFilter(
            JwtIssuer jwtIssuer,
            UserAccountRepository userAccountRepository,
            GlobalExceptionHandler exceptionHandler) {
        this.jwtIssuer = jwtIssuer;
        this.userAccountRepository = userAccountRepository;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("Unsupported authorization header.");
        }

        try {
            JwtIssuer.VerifiedAccessToken token = jwtIssuer.verify(authorization.substring(BEARER_PREFIX.length()));
            User user = userAccountRepository.findById(token.userId())
                    .filter(candidate -> candidate.status() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new BadCredentialsException("User is not active."));
            FoodMindPrincipal principal = new FoodMindPrincipal(
                    user.id(),
                    user.email(),
                    user.displayName(),
                    user.role(),
                    user.status());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            exceptionHandler.handleAuthenticationFailure(request, response, exception);
        }
    }
}
