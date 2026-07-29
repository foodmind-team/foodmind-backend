package com.foodmind.foodmindbackend.user.application.port;

import com.foodmind.foodmindbackend.user.domain.User;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public interface UserAccountRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByNormalisedEmail(String normalisedEmail);

    boolean existsByNormalisedEmail(String normalisedEmail);

    void updateLastLoginAt(UUID id, OffsetDateTime lastLoginAt);
}
