package com.foodmind.foodmindbackend.auth.application;

import com.foodmind.foodmindbackend.auth.domain.ClientType;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.user.application.EmailNormalizer;
import com.foodmind.foodmindbackend.user.application.port.UserAccountRepository;
import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.domain.UserStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Service
public class LoginUser {

    private final UserAccountRepository userAccountRepository;
    private final EmailNormalizer emailNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    public LoginUser(
            UserAccountRepository userAccountRepository,
            EmailNormalizer emailNormalizer,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.emailNormalizer = emailNormalizer;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthTokens login(Command command) {
        User user = userAccountRepository.findByNormalisedEmail(emailNormalizer.normalize(command.email()))
                .orElseThrow(this::authenticationFailed);
        if (!passwordEncoder.matches(command.password(), user.passwordHash())) {
            throw authenticationFailed();
        }
        if (user.status() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Account is not active.");
        }
        userAccountRepository.updateLastLoginAt(user.id(), OffsetDateTime.now(clock));
        return authTokenService.issueNewSession(user, command.clientType(), command.deviceLabel());
    }

    private ApiException authenticationFailed() {
        return new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    public record Command(
            String email,
            String password,
            ClientType clientType,
            String deviceLabel) {
    }
}
