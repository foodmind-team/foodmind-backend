package com.foodmind.foodmindbackend.user.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.user.application.port.UserAccountRepository;
import com.foodmind.foodmindbackend.user.domain.User;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Service
public class GetCurrentUser {

    private final UserAccountRepository userAccountRepository;

    public GetCurrentUser(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public User get(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED));
    }
}
