package com.foodmind.foodmindbackend.auth.application;

import com.foodmind.foodmindbackend.auth.domain.ClientType;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.user.application.EmailNormalizer;
import com.foodmind.foodmindbackend.user.application.port.UserAccountRepository;
import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.domain.UserRole;
import com.foodmind.foodmindbackend.user.domain.UserStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
public class RegisterUser {

    private final UserAccountRepository userAccountRepository;
    private final EmailNormalizer emailNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    public RegisterUser(
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
    public AuthTokens register(Command command) {
        String email = command.email().trim();
        String normalisedEmail = emailNormalizer.normalize(email);
        if (userAccountRepository.existsByNormalisedEmail(normalisedEmail)) {
            throw duplicateEmail();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        User user = new User(
                UUID.randomUUID(),
                email,
                normalisedEmail,
                passwordEncoder.encode(command.password()),
                command.displayName().trim(),
                UserRole.USER,
                UserStatus.ACTIVE,
                timeZoneOrDefault(command.timeZone()),
                now,
                now,
                null,
                null,
                0);
        try {
            User saved = userAccountRepository.save(user);
            return authTokenService.issueNewSession(saved, command.clientType(), command.deviceLabel());
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEmail();
        }
    }

    private String timeZoneOrDefault(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return "Asia/Singapore";
        }
        return timeZone.trim();
    }

    private ApiException duplicateEmail() {
        return new ApiException(ErrorCode.CONFLICT, "Email is already registered.");
    }

    public record Command(
            String email,
            String displayName,
            String password,
            String timeZone,
            ClientType clientType,
            String deviceLabel) {
    }
}
