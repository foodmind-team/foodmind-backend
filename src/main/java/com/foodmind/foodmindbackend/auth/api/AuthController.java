package com.foodmind.foodmindbackend.auth.api;

import com.foodmind.foodmindbackend.auth.api.request.LoginRequest;
import com.foodmind.foodmindbackend.auth.api.request.RefreshRequest;
import com.foodmind.foodmindbackend.auth.api.request.RegisterRequest;
import com.foodmind.foodmindbackend.auth.api.response.AuthTokenResponse;
import com.foodmind.foodmindbackend.auth.application.AuthTokens;
import com.foodmind.foodmindbackend.auth.application.LoginUser;
import com.foodmind.foodmindbackend.auth.application.LogoutSession;
import com.foodmind.foodmindbackend.auth.application.RefreshSession;
import com.foodmind.foodmindbackend.auth.application.RegisterUser;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "FM_REFRESH";
    static final String CSRF_COOKIE = "FM_CSRF";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final RegisterUser registerUser;
    private final LoginUser loginUser;
    private final RefreshSession refreshSession;
    private final LogoutSession logoutSession;

    public AuthController(
            RegisterUser registerUser,
            LoginUser loginUser,
            RefreshSession refreshSession,
            LogoutSession logoutSession) {
        this.registerUser = registerUser;
        this.loginUser = loginUser;
        this.refreshSession = refreshSession;
        this.logoutSession = logoutSession;
    }

    @PostMapping("/register")
    ResponseEntity<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthTokens tokens = registerUser.register(new RegisterUser.Command(
                request.email(),
                request.displayName(),
                request.password(),
                request.timeZone(),
                request.clientType(),
                request.deviceLabel()));
        return tokenResponse(tokens, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokens tokens = loginUser.login(new LoginUser.Command(
                request.email(),
                request.password(),
                request.clientType(),
                request.deviceLabel()));
        return tokenResponse(tokens, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthTokenResponse> refresh(
            @Valid @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie) {
        AuthTokens tokens = refreshSession.refresh(refreshToken(request, refreshCookie));
        return tokenResponse(tokens, HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @Valid @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie) {
        logoutSession.logout(refreshTokenOrNull(request, refreshCookie));
        return ResponseEntity.noContent()
                .headers(clearCookieHeaders())
                .build();
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@AuthenticationPrincipal FoodMindPrincipal principal) {
        logoutSession.logoutAll(principal.id());
        return ResponseEntity.noContent()
                .headers(clearCookieHeaders())
                .build();
    }

    private ResponseEntity<AuthTokenResponse> tokenResponse(AuthTokens tokens, HttpStatus status) {
        String csrfToken = csrfToken();
        return ResponseEntity.status(status)
                .headers(cookieHeaders(tokens, csrfToken))
                .body(AuthTokenResponse.from(tokens, csrfToken));
    }

    private String refreshToken(RefreshRequest request, String refreshCookie) {
        String resolved = refreshTokenOrNull(request, refreshCookie);
        if (resolved == null) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Refresh session is invalid.");
        }
        return resolved;
    }

    private String refreshTokenOrNull(RefreshRequest request, String refreshCookie) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            return refreshCookie;
        }
        return null;
    }

    private HttpHeaders cookieHeaders(AuthTokens tokens, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, tokens.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(java.time.Duration.between(java.time.OffsetDateTime.now(), tokens.refreshTokenExpiresAt()))
                .build()
                .toString());
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(CSRF_COOKIE, csrfToken)
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(java.time.Duration.between(java.time.OffsetDateTime.now(), tokens.refreshTokenExpiresAt()))
                .build()
                .toString());
        return headers;
    }

    private HttpHeaders clearCookieHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build()
                .toString());
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(CSRF_COOKIE, "")
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build()
                .toString());
        return headers;
    }

    private String csrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
